package org.b3nk3i.akka.persistence.firestore.snapshot

import akka.persistence.SnapshotMetadata
import akka.stream.Materializer
import cats.implicits.{toFunctorOps, toTraverseOps}
import com.google.cloud.firestore.{Firestore, Query}
import org.b3nk3i.akka.persistence.firestore.data.Field
import org.b3nk3i.akka.persistence.firestore.journal.FireStoreDao.asFirestoreRepr
import org.b3nk3i.akka.persistence.firestore.journal.FirestorePersistentRepr
import org.b3nk3i.akka.persistence.firestore.snapshot.FireStoreSnapshotDao.SnapshotStore
import org.b3nk3i.google.ApiFuturesOps.ApiFutureExt
import org.b3nk3i.google.FirestoreStreamingOps.StreamQueryOps

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters._

class FireStoreSnapshotDao(
  db:             Firestore,
  rootCollection: String,
  queueSize:      Int,
  enqueueTimeout: Duration,
  parallelism:    Int
)(implicit ec:    ExecutionContextExecutor, mat: Materializer) {

  def delete(metadata: SnapshotMetadata): Future[Unit] = {
    db.collection(rootCollection)
      .document(metadata.persistenceId)
      .collection(SnapshotStore)
      .document(metadata.sequenceNr.toString)
      .delete()
      .futureLift
      .void
  }

  def deleteAllSnapshots(persistenceId: String): Future[Unit] = {
    db.collection(rootCollection)
      .document(persistenceId)
      .collection(SnapshotStore)
      .listDocuments()
      .asScala
      .toList
      .traverse(_.delete().futureLift)
      .void
  }

  def deleteUntilMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Unit] = {
    val until = com.google.cloud.Timestamp.of(new java.sql.Timestamp(maxTimestamp))

    db.collection(rootCollection)
      .document(persistenceId)
      .collection(SnapshotStore)
      .whereLessThanOrEqualTo(Field.Timestamp.name, until)
      .orderBy(Field.Timestamp.name, Query.Direction.DESCENDING)
      .toStream(queueSize, enqueueTimeout)
      .mapAsyncUnordered(parallelism)(_.getReference.delete().futureLift)
      .run()
      .void
  }

  def deleteUntilMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Unit] = {
    db.collection(rootCollection)
      .document(persistenceId)
      .collection(SnapshotStore)
      .whereLessThanOrEqualTo(Field.Sequence.name, maxSequenceNr)
      .orderBy(Field.Sequence.name, Query.Direction.DESCENDING)
      .toStream(queueSize, enqueueTimeout)
      .mapAsyncUnordered(parallelism)(_.getReference.delete().futureLift)
      .run()
      .void
  }

  def deleteUntilMaxSequenceAndMaxTimestamp(
    persistenceId: String,
    maxSequenceNr: Long,
    maxTimestamp:  Long
  ): Future[Unit] = {

    val until = com.google.cloud.Timestamp.of(new java.sql.Timestamp(maxTimestamp))

    // Cannot have inequality filters on multiple properties so 2nd criteria handled with find
    db.collection(rootCollection)
      .document(persistenceId)
      .collection(SnapshotStore)
      .whereLessThanOrEqualTo(Field.Timestamp.name, until)
      .orderBy(Field.Timestamp.name, Query.Direction.DESCENDING)
      .toStream(queueSize, enqueueTimeout)
      .mapAsyncUnordered(parallelism)(asFirestoreRepr(persistenceId, _))
      .mapAsyncUnordered(parallelism)(
        s =>
          Option(s).filter(_.sequence <= maxSequenceNr) match {
            case Some(doc) => delete(SnapshotMetadata(doc.persistenceId, doc.sequence))
            case None      => Future.unit
          }
      )
      .run()
      .void
  }

  def save(snapshot: FirestorePersistentRepr): Future[Unit] = {

    db.collection(rootCollection)
      .document(snapshot.persistenceId)
      .collection(SnapshotStore)
      .document(snapshot.sequence.toString)
      .set(snapshot.data.asJava)
      .futureLift
      .void
  }

  def snapshotForMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Option[FirestorePersistentRepr]] = {
    db.collection(rootCollection)
      .document(persistenceId)
      .collection(SnapshotStore)
      .whereLessThanOrEqualTo(Field.Sequence.name, maxSequenceNr)
      .orderBy(Field.Sequence.name, Query.Direction.DESCENDING)
      .limit(1)
      .get()
      .futureLift
      .map(_.getDocuments.asScala.toList.headOption)
      .flatMap(maybeSnapshot => maybeSnapshot.traverse(asFirestoreRepr(persistenceId, _)))
  }

  def snapshotForMaxSequenceNrAndMaxTimestamp(
    persistenceId: String,
    maxSequenceNr: Long,
    maxTimestamp:  Long
  ): Future[Option[FirestorePersistentRepr]] = {

    val until = com.google.cloud.Timestamp.of(new java.sql.Timestamp(maxTimestamp))

    // Cannot have inequality filters on multiple properties so 2nd criteria handled with find
    db.collection(rootCollection)
      .document(persistenceId)
      .collection(SnapshotStore)
      .whereLessThanOrEqualTo(Field.Timestamp.name, until)
      .orderBy(Field.Timestamp.name, Query.Direction.DESCENDING)
      .get()
      .futureLift
      .map(_.getDocuments.asScala.toList)
      .flatMap { maybeSnapshot =>
        maybeSnapshot
          .traverse(asFirestoreRepr(persistenceId, _))
          .map(_.find(_.sequence <= maxSequenceNr))
      }
  }

  def latestSnapshot(persistenceId: String): Future[Option[FirestorePersistentRepr]] = {
    db.collection(rootCollection)
      .document(persistenceId)
      .collection(SnapshotStore)
      .orderBy(Field.Sequence.name, Query.Direction.DESCENDING)
      .limit(1)
      .get()
      .futureLift
      .map(_.getDocuments.asScala.toList.headOption)
      .flatMap(maybeSnapshot => maybeSnapshot.traverse(asFirestoreRepr(persistenceId, _)))
  }

  def snapshotForMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Option[FirestorePersistentRepr]] = {
    val until = com.google.cloud.Timestamp.of(new java.sql.Timestamp(maxTimestamp))

    db.collection(rootCollection)
      .document(persistenceId)
      .collection(SnapshotStore)
      .whereLessThanOrEqualTo(Field.Timestamp.name, until)
      .orderBy(Field.Timestamp.name, Query.Direction.DESCENDING)
      .limit(1)
      .get()
      .futureLift
      .map(_.getDocuments.asScala.toList.headOption)
      .flatMap(maybeSnapshot => maybeSnapshot.traverse(asFirestoreRepr(persistenceId, _)))
  }
}

object FireStoreSnapshotDao {

  val SnapshotStore = "snapshot-store"

}
