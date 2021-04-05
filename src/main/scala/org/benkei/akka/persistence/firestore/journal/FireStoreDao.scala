package org.benkei.akka.persistence.firestore.journal

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import cats.implicits.toFunctorOps
import com.fasterxml.uuid.Generators
import com.google.cloud.firestore.{DocumentSnapshot, FieldValue, Firestore, Query}
import org.benkei.akka.persistence.firestore.data.Document.Document
import org.benkei.akka.persistence.firestore.data.Field
import org.benkei.akka.persistence.firestore.journal.FireStoreDao.{EventJournal, asFirestoreRepr}
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

  def write(events: Seq[FirestorePersistentRepr]): Future[Unit] = {

    val batch = db.batch()

    events.foreach { event =>
      val metaData: Document =
        Map(
          Field.Ordering.name  -> Generators.timeBasedGenerator().generate().toString,
          Field.Timestamp.name -> FieldValue.serverTimestamp()
        )

      batch.create(
        db.collection(rootCollection)
          .document(event.persistenceId)
          .collection(EventJournal)
          .document(event.sequence.toString),
        (event.data ++ metaData).asJava
      )
    }

    batch.commit().futureLift.void
  }

  def softDelete(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    db.collection(rootCollection)
      .document(persistenceId)
      .collection(EventJournal)
      .whereLessThanOrEqualTo(Field.Sequence.name, toSequenceNr)
      .orderBy(Field.Sequence.name, Query.Direction.ASCENDING)
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
      .collection(EventJournal)
      .whereGreaterThanOrEqualTo(Field.Sequence.name, fromSequenceNr)
      .orderBy(Field.Sequence.name, Query.Direction.ASCENDING)
      .toStream(queueSize, enqueueTimeout)
      .map(_.getId.toLong)
      .runFold(fromSequenceNr)(math.max)
  }

  def persistenceIds(): Source[String, NotUsed] = {
    Source
      .fromIterator(() => db.collection(rootCollection).listDocuments().iterator().asScala)
      .map(ref => ref.getId)
  }

  def events(
    persistenceId:  String,
    fromSequenceNr: Long,
    toSequenceNr:   Long
  ): Source[FirestorePersistentRepr, NotUsed] = {
    db.collection(rootCollection)
      .document(persistenceId)
      .collection(EventJournal)
      .whereGreaterThanOrEqualTo(Field.Sequence.name, fromSequenceNr)
      .whereLessThanOrEqualTo(Field.Sequence.name, toSequenceNr)
      .orderBy(Field.Sequence.name, Query.Direction.ASCENDING)
      .toStream(queueSize, enqueueTimeout)
      .mapAsync(parallelism)(event => asFirestoreRepr(persistenceId, event))
  }

  def eventsByTag(tag: String, offset: String): Source[FirestorePersistentRepr, NotUsed] = {
    db.collectionGroup(EventJournal)
      .whereGreaterThan(Field.Ordering.name, offset)
      .whereArrayContains(Field.Tags.name, tag)
      .orderBy(Field.Ordering.name, Query.Direction.ASCENDING)
      .toStream(queueSize, enqueueTimeout)
      .mapAsync(parallelism)(event => asFirestoreRepr(event.getReference.getParent.getParent.getId, event))
  }
}

object FireStoreDao {

  val EventJournal = "event-journal"

  def asFirestoreRepr(persistenceId: String, result: DocumentSnapshot): Future[FirestorePersistentRepr] = {
    Option(result).filter(_.exists()) match {
      case Some(doc) =>
        Future.successful(FirestorePersistentRepr(persistenceId, doc.getId.toLong, doc.getData.asScala.toMap))
      case None =>
        Future.failed(new NoSuchElementException(s"Document not found ${result.getId}"))
    }
  }
}
