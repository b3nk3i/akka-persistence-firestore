package org.benkei.akka.persistence.firestore.journal

import cats.implicits.{toFunctorOps, toTraverseOps}
import com.google.cloud.firestore.{DocumentSnapshot, Firestore}
import org.benkei.akka.persistence.firestore.journal.FireStoreDao.asRepr
import org.benkei.google.ApiFuturesOps.ApiFutureExt

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters._

class FireStoreDao(db: Firestore, rootCollection: String)(implicit ec: ExecutionContextExecutor) {

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
      .whereLessThanOrEqualTo("sequence", toSequenceNr)
      .get()
      .futureLift
      .map(_.getDocuments.asScala)
      .flatMap(events => events.toList.traverse(_.getReference.update("deleted", true).futureLift))
      .void
  }

  def read(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Future[List[FirestorePersistentRepr]] = {
    db.collection(rootCollection)
      .document(persistenceId)
      .collection("event-journal")
      .whereGreaterThanOrEqualTo("sequence", fromSequenceNr)
      .whereLessThanOrEqualTo("sequence", toSequenceNr)
      .get()
      .futureLift
      .map(_.getDocuments.asScala)
      .flatMap(events => events.toList.traverse(asRepr(persistenceId, _)))
  }
}

object FireStoreDao {

  def asRepr(persistenceId: String, result: DocumentSnapshot): Future[FirestorePersistentRepr] = {
    Option(result).filter(_.exists()) match {
      case Some(doc) =>
        Future.successful(FirestorePersistentRepr(persistenceId, doc.getId.toLong, doc.getData.asScala.toMap))
      case None =>
        Future.failed(new NoSuchElementException(s"Document not found ${result.getId}"))
    }
  }
}
