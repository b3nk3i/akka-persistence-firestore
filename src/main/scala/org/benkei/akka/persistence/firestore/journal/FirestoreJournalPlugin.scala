package org.benkei.akka.persistence.firestore.journal

import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.serialization.SerializationExtension
import cats.implicits.{toFunctorOps, toTraverseOps}
import com.google.cloud.firestore.Firestore
import com.typesafe.config.Config
import org.benkei.akka.persistence.firestore.client.FireStoreExtension
import org.benkei.google.ApiFuturesOps.ApiFutureExt

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try

/*
  FirestoreJournalPlugin for Akka persistence that implements the read and write queries.
 */
class FirestoreJournalPlugin(config: Config) extends AsyncWriteJournal {

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  val db: Firestore = FireStoreExtension(context.system).client(config)

  val rootCollection: String = {
    config.getString("root")
  }

  val serializer: FirestoreSerializer = {
    FirestoreSerializer(SerializationExtension(context.system))
  }

  val dao = new FireStoreDao(db, rootCollection)

  def serialize(aw: AtomicWrite): Seq[Try[FirestorePersistentRepr]] = {
    aw.payload.map(serializer.serialize)
  }

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    messages
      .flatMap(serialize)
      .traverse { maybeEvt =>
        maybeEvt.traverse(dao.write)
      }
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    dao.softDelete(persistenceId, toSequenceNr)
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
    recoveryCallback:                             PersistentRepr => Unit
  ): Future[Unit] = {

    dao
      .read(persistenceId, fromSequenceNr, toSequenceNr)
      .flatMap(rows => rows.traverse(row => Future.fromTry(serializer.deserialize(row).map(recoveryCallback))).void)
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    db.collection(rootCollection)
      .document(persistenceId)
      .collection("event-journal")
      .whereGreaterThanOrEqualTo("sequence", fromSequenceNr)
      .get()
      .futureLift
      .map(_.getDocuments.asScala.map(_.getId.toLong).maxOption.getOrElse(fromSequenceNr))
  }
}
