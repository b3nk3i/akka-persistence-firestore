package org.benkei.akka.persistence.firestore

import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import cats.implicits.{toFunctorOps, toTraverseOps}
import com.google.cloud.firestore.{DocumentSnapshot, Firestore}
import org.benkei.google.ApiFuturesOps.ApiFutureExt

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try

/*
  FirestoreJournalPlugin for Akka persistence that implements the read and write queries.
 */
trait FirestoreJournalPlugin extends AsyncWriteJournal {

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  def db: Firestore

  def rootCollection: String

  def serializer: FirestoreSerializer

  def serialize(aw: AtomicWrite): Seq[Try[FirestorePersistentRepr]] = {
    aw.payload.map(serializer.serialize)
  }

  def read(persistenceId: String, result: DocumentSnapshot): Future[PersistentRepr] = {
    Option(result).filter(_.exists()) match {
      case Some(doc) =>
        val fpr =
          FirestorePersistentRepr(persistenceId, doc.getId.toLong, doc.getData.asScala.toMap)
        Future.fromTry(serializer.deserialize(fpr))
      case None =>
        Future.failed(new NoSuchElementException(s"Document not found ${result.getId}"))
    }
  }

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    messages
      .flatMap { message =>
        serialize(message)
      }
      .traverse { maybeEvt =>
        maybeEvt.traverse { evt =>
          db.collection(rootCollection)
            .document(evt.persistenceId)
            .collection("event-journal")
            .document(evt.sequence.toString)
            .create(evt.data.asJava)
            .futureLift
            .void
        }
      }
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    db.collection(rootCollection)
      .document(persistenceId)
      .collection("event-journal")
      .whereLessThanOrEqualTo("sequence", toSequenceNr)
      .get()
      .futureLift
      .map(_.getDocuments.asScala)
      .flatMap(events => events.toList.traverse(_.getReference.update("deleted", true).futureLift))
      .void
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
    recoveryCallback:                             PersistentRepr => Unit
  ): Future[Unit] = {

    db.collection(rootCollection)
      .document(persistenceId)
      .collection("event-journal")
      .whereGreaterThanOrEqualTo("sequence", fromSequenceNr)
      .whereLessThanOrEqualTo("sequence", toSequenceNr)
      .get()
      .futureLift
      .map(_.getDocuments.asScala)
      .flatMap(events => events.toList.traverse(read(persistenceId, _).map(recoveryCallback)))
      .void
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
