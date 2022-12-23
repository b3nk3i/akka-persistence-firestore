package org.b3nk3i.akka.persistence.firestore.snapshot

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.stream.{Materializer, SystemMaterializer}
import cats.implicits.toTraverseOps
import com.google.cloud.firestore.Firestore
import com.typesafe.config.Config
import org.b3nk3i.akka.persistence.firestore.client.FireStoreExtension
import org.b3nk3i.akka.persistence.firestore.config.FirestoreSnapshotConfig
import org.b3nk3i.akka.persistence.firestore.journal.FirestorePersistentRepr
import org.b3nk3i.akka.persistence.firestore.serialization.extention.FirestorePayloadSerializerExtension

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

class FirestoreSnapshotStore(config: Config) extends SnapshotStore {

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  implicit val mat: Materializer = SystemMaterializer(context.system).materializer

  val db: Firestore = FireStoreExtension(context.system).client(config)

  CoordinatedShutdown(context.system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "closeSnapshotFirestore") {
    () => Future(db.close()).map(_ => Done)
  }

  val serializer: FirestoreSnapshotSerializer = {
    FirestoreSnapshotSerializer(FirestorePayloadSerializerExtension(context.system).payloadSerializer(config))
  }

  val snapshotConfig = FirestoreSnapshotConfig(config)

  val snapshotDao = new FireStoreSnapshotDao(db, snapshotConfig.rootCollection, snapshotConfig.enqueueTimeout)

  override def loadAsync(
    persistenceId: String,
    criteria:      SnapshotSelectionCriteria
  ): Future[Option[SelectedSnapshot]] = {

    log.info(s"SnapshotStore loadAsync $persistenceId $criteria")

    val result: Future[Option[FirestorePersistentRepr]] = criteria match {
      case SnapshotSelectionCriteria(Long.MaxValue, Long.MaxValue, _, _) =>
        snapshotDao.latestSnapshot(persistenceId)
      case SnapshotSelectionCriteria(Long.MaxValue, maxTimestamp, _, _) =>
        snapshotDao.snapshotForMaxTimestamp(persistenceId, maxTimestamp)
      case SnapshotSelectionCriteria(maxSequenceNr, Long.MaxValue, _, _) =>
        snapshotDao.snapshotForMaxSequenceNr(persistenceId, maxSequenceNr)
      case SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, _, _) =>
        snapshotDao.snapshotForMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)
      case _ =>
        Future.successful(None)
    }

    result.flatMap(_.traverse(s => Future.fromTry(toSelectedSnapshot(s))))
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    Future
      .fromTry(serializer.serialize(metadata, snapshot))
      .flatMap(snapshotDao.save)
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    snapshotDao.delete(metadata)
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    ???
  }

  private def toSelectedSnapshot(pr: FirestorePersistentRepr): Try[SelectedSnapshot] = {
    for {
      timestamp <- serializer.timestamp(pr)
      snapshot  <- serializer.deserialize(pr)
    } yield {
      SelectedSnapshot(SnapshotMetadata(pr.persistenceId, pr.sequence, timestamp), snapshot)
    }
  }
}
