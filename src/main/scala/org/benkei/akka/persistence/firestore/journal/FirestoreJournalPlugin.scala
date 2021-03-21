package org.benkei.akka.persistence.firestore.journal

import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.Source
import akka.stream.{Materializer, SystemMaterializer}
import cats.implicits._
import com.google.cloud.firestore.Firestore
import com.typesafe.config.Config
import org.benkei.akka.persistence.firestore.client.FireStoreExtension
import org.benkei.akka.persistence.firestore.config.FirestoreJournalConfig

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

/*
  FirestoreJournalPlugin for Akka persistence that implements the read and write queries.
 */
class FirestoreJournalPlugin(config: Config) extends AsyncWriteJournal {

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  implicit val mat: Materializer = SystemMaterializer(context.system).materializer

  val db: Firestore = FireStoreExtension(context.system).client(config)

  val serializer: FirestoreSerializer = {
    FirestoreSerializer(SerializationExtension(context.system))
  }

  val journalConfig: FirestoreJournalConfig = FirestoreJournalConfig(config)

  val dao = new FireStoreDao(
    db,
    journalConfig.rootCollection,
    journalConfig.queueSize,
    journalConfig.enqueueTimeout,
    journalConfig.parallelism
  )

  def serialize(aw: AtomicWrite): Try[Seq[FirestorePersistentRepr]] = {
    aw.payload.traverse(serializer.serialize)
  }

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    Source
      .fromIterator(() => messages.iterator)
      .map { atomicWrite => serialize(atomicWrite) }
      .mapAsync(1) { maybeEvents => maybeEvents.traverse(dao.write) }
      .runFold(Seq.empty[Try[Unit]]) { _ :+ _ }
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    dao.softDelete(persistenceId, toSequenceNr)
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
    recoveryCallback:                             PersistentRepr => Unit
  ): Future[Unit] = {

    dao
      .read(persistenceId, fromSequenceNr, toSequenceNr, max)
      .mapAsync(1)(row => Future.fromTry(serializer.deserialize(row)))
      .map(recoveryCallback)
      .run()
      .void
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    dao.readMaxSequenceNr(persistenceId, fromSequenceNr)
  }
}
