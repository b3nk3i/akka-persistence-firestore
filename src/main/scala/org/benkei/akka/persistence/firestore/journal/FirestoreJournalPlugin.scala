package org.benkei.akka.persistence.firestore.journal

import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.serialization.SerializationExtension
import akka.stream.Materializer
import cats.implicits._
import com.google.cloud.firestore.Firestore
import com.typesafe.config.Config
import org.benkei.akka.persistence.firestore.client.FireStoreExtension

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

/*
  FirestoreJournalPlugin for Akka persistence that implements the read and write queries.
 */
class FirestoreJournalPlugin(config: Config) extends AsyncWriteJournal {

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  implicit val mat: Materializer = Materializer(context.system)

  val db: Firestore = FireStoreExtension(context.system).client(config)

  val rootCollection: String = {
    config.getString("root")
  }

  val enqueueTimeout: FiniteDuration = {
    val t = config.getDuration("enqueue-timeout")
    FiniteDuration(t.toMillis, TimeUnit.MILLISECONDS)
  }

  val queueSize: Int = {
    config.getInt("queue-size")
  }

  val parallelism: Int = {
    config.getInt("parallelism")
  }

  val serializer: FirestoreSerializer = {
    FirestoreSerializer(SerializationExtension(context.system))
  }

  val dao = new FireStoreDao(db, rootCollection, queueSize, enqueueTimeout, parallelism)

  def serialize(aw: AtomicWrite): Try[Seq[FirestorePersistentRepr]] = {
    aw.payload.traverse(serializer.serialize)
  }

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    messages
      .traverse(atomicWrite => serialize(atomicWrite).traverse(pr => pr.traverse(dao.write).map(_ => ())))
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
