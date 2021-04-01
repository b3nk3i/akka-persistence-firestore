package org.benkei.akka.persistence.firestore.journal

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.event.{Logging, LoggingAdapter}
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.stream.scaladsl.Source
import akka.stream.{Materializer, SystemMaterializer}
import cats.implicits._
import com.google.cloud.firestore.Firestore
import com.typesafe.config.Config
import org.benkei.akka.persistence.firestore.client.FireStoreExtension
import org.benkei.akka.persistence.firestore.config.FirestoreJournalConfig
import org.benkei.akka.persistence.firestore.serialization.FirestoreSerializer
import org.benkei.akka.persistence.firestore.serialization.extention.FirestorePayloadSerializerExtension

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

/*
  FirestoreJournalPlugin for Akka persistence that implements the read and write queries.
 */
class FirestoreJournalPlugin(config: Config) extends AsyncWriteJournal {

  private val log: LoggingAdapter = Logging(context.system, getClass)

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  implicit val mat: Materializer = SystemMaterializer(context.system).materializer

  val db: Firestore = FireStoreExtension(context.system).client(config)

  CoordinatedShutdown(context.system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "closeJournalFirestore") {
    () => Future(db.close()).map(_ => Done)
  }

  val serializer: FirestoreSerializer = {
    FirestoreSerializer(FirestorePayloadSerializerExtension(context.system).payloadSerializer(config))
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

    val from = messages.headOption

    val (persistenceId, fromSequenceNr) = from.map(aw => (aw.persistenceId, aw.lowestSequenceNr)).getOrElse(("", ""))

    /*
      persistAll triggers asyncWriteMessages with a Seq of events, it is assumed they have the same persistenceId
     */
    log.debug(
      "asyncWriteMessages from sequence number [{}] for persistenceId [{}] [{}]",
      fromSequenceNr,
      persistenceId,
      sender()
    )

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
