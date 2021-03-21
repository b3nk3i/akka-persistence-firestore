package org.benkei.akka.persistence.firestore.journal

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import cats.implicits.toFunctorOps
import com.google.cloud.firestore.{DocumentSnapshot, Firestore}
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

  def write(evt: FirestorePersistentRepr): Future[Unit] = {
    db.collection(rootCollection)
      .document(evt.persistenceId)
      .collection("event-journal")
      .document(evt.sequence.toString)
      .create(evt.data.asJava)
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
    db.collection(rootCollection)
      .select(Field.PersistenceID.name)
      .toStream(queueSize, enqueueTimeout)
      .map(_.getId)
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
