package org.benkei.akka.persistence.firestore.journal

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import cats.implicits.toFunctorOps
import com.google.cloud.firestore.{DocumentSnapshot, Firestore}
import org.benkei.akka.persistence.firestore.data.Document.Document
import org.benkei.akka.persistence.firestore.data.Field
import org.benkei.akka.persistence.firestore.journal.FireStoreDao.asFirestoreRepr
import org.benkei.google.ApiFuturesOps.ApiFutureExt
import org.benkei.google.FirestoreStreamingOps.StreamQueryOps

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters._

class FireStoreDao(db: Firestore, rootCollection: String, queueSize: Int, enqueueTimeout: Duration, parallelism: Int)(
  implicit
  ec:  ExecutionContextExecutor,
  mat: Materializer
) {

  val Sequences = "sequences"

  def write(events: Seq[FirestorePersistentRepr]): Future[Unit] = {

    val journalSequence = db.collection(rootCollection).document(Sequences)

    db.runTransaction { transaction =>
        val currentOrdering =
          transaction.get(journalSequence).get().getLong(Field.Ordering.name)

        val lastOrdering =
          events.foldLeft(currentOrdering) {
            case (acc, event) =>
              val next = acc + 1

              val ordering: Document = Map(Field.Ordering.name -> next)

              transaction.create(
                db.collection(rootCollection)
                  .document(event.persistenceId)
                  .collection("event-journal")
                  .document(event.sequence.toString),
                (event.data ++ ordering).asJava
              )
              next
          }

        transaction.update(journalSequence, Field.Ordering.name, lastOrdering)
      }
      .futureLift
      .void
  }

  def softDelete(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    db.collection(rootCollection)
      .document(persistenceId)
      .collection("event-journal")
      .whereLessThanOrEqualTo(Field.Sequence.name, toSequenceNr)
      .orderBy(Field.Sequence.name)
      .toStream(queueSize, enqueueTimeout)
      .mapAsyncUnordered(parallelism)(event => event.getReference.update("deleted", true).futureLift)
      .run()
      .void
  }

  def read(
    persistenceId:  String,
    fromSequenceNr: Long,
    toSequenceNr:   Long,
    max:            Long
  ): Source[FirestorePersistentRepr, NotUsed] = {
    events(persistenceId, fromSequenceNr, toSequenceNr).take(max)
  }

  def readMaxSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    db.collection(rootCollection)
      .document(persistenceId)
      .collection("event-journal")
      .whereGreaterThanOrEqualTo(Field.Sequence.name, fromSequenceNr)
      .orderBy(Field.Sequence.name)
      .toStream(queueSize, enqueueTimeout)
      .map(_.getId.toLong)
      .runFold(fromSequenceNr)(math.max)
  }

  def persistenceIds(): Source[String, NotUsed] = {
    Source
      .fromIterator(() => db.collection(rootCollection).listDocuments().iterator().asScala)
      .map(ref => ref.getId)
      .filterNot(_ == Sequences)
  }

  def events(
    persistenceId:  String,
    fromSequenceNr: Long,
    toSequenceNr:   Long
  ): Source[FirestorePersistentRepr, NotUsed] = {
    db.collection(rootCollection)
      .document(persistenceId)
      .collection("event-journal")
      .whereGreaterThanOrEqualTo(Field.Sequence.name, fromSequenceNr)
      .whereLessThanOrEqualTo(Field.Sequence.name, toSequenceNr)
      .orderBy(Field.Sequence.name)
      .toStream(queueSize, enqueueTimeout)
      .mapAsync(parallelism)(event => asFirestoreRepr(persistenceId, event))
  }

  def eventsByTag(tag: String, offset: Long): Source[FirestorePersistentRepr, NotUsed] = {
    db.collectionGroup("event-journal")
      .whereGreaterThan(Field.Ordering.name, offset)
      .whereArrayContains(Field.Tags.name, tag)
      .orderBy(Field.Ordering.name)
      .toStream(queueSize, enqueueTimeout)
      .mapAsync(parallelism)(event => asFirestoreRepr(event.getReference.getParent.getParent.getId, event))
  }
}

object FireStoreDao {

  def asFirestoreRepr(persistenceId: String, result: DocumentSnapshot): Future[FirestorePersistentRepr] = {
    Option(result).filter(_.exists()) match {
      case Some(doc) =>
        Future.successful(FirestorePersistentRepr(persistenceId, doc.getId.toLong, doc.getData.asScala.toMap))
      case None =>
        Future.failed(new NoSuchElementException(s"Document not found ${result.getId}"))
    }
  }
}
