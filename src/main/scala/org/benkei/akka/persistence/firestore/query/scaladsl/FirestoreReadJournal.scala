package org.benkei.akka.persistence.firestore.query
package scaladsl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.query._
import akka.persistence.query.scaladsl._
import akka.persistence.{Persistence, PersistentRepr}
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.Source
import akka.stream.{Materializer, SystemMaterializer}
import com.google.cloud.firestore.Firestore
import com.typesafe.config.Config
import org.benkei.akka.persistence.firestore.client.FireStoreExtension
import org.benkei.akka.persistence.firestore.config.{FirestoreJournalConfig, FirestoreReadJournalConfig}
import org.benkei.akka.persistence.firestore.journal.{FireStoreDao, FirestoreSerializer}

import scala.collection.immutable.Seq
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}

object FirestoreReadJournal {
  final val Identifier = "firestore-read-journal"
}

class FirestoreReadJournal(config: Config, configPath: String)(implicit val system: ActorSystem)
    extends ReadJournal
    with CurrentPersistenceIdsQuery
    with PersistenceIdsQuery
    with CurrentEventsByPersistenceIdQuery
    with EventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with EventsByTagQuery {

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  implicit val mat: Materializer = SystemMaterializer(system).materializer

  val db: Firestore = FireStoreExtension(system).client(config)

  val journalConfig: FirestoreJournalConfig = FirestoreJournalConfig(config)

  val readJournalConfig: FirestoreReadJournalConfig = FirestoreReadJournalConfig(config)

  private val writePluginId = config.getString("write-plugin")

  // If 'config' is empty, or if the plugin reference is not found, then the write plugin will be resolved from the
  // ActorSystem configuration. Otherwise, it will be resolved from the provided 'config'.
  private val eventAdapters = Persistence(system).adaptersFor(writePluginId, config)

  private val delaySource =
    Source.tick(readJournalConfig.refreshInterval, 0.seconds, 0).take(1)

  val serializer: FirestoreSerializer = {
    FirestoreSerializer(SerializationExtension(system))
  }

  val dao = new FireStoreDao(
    db,
    journalConfig.rootCollection,
    journalConfig.queueSize,
    journalConfig.enqueueTimeout,
    journalConfig.parallelism
  )

  override def currentPersistenceIds(): Source[String, NotUsed] = {
    dao.persistenceIds()
  }

  /**
    * Same type of query as `eventsByPersistenceId` but the event stream
    * is completed immediately when it reaches the end of the "result set". Events that are
    * stored after the query is completed are not included in the event stream.
    */
  override def currentEventsByPersistenceId(
    persistenceId:  String,
    fromSequenceNr: Long,
    toSequenceNr:   Long
  ): Source[EventEnvelope, NotUsed] = {
    dao
      .events(persistenceId, fromSequenceNr, toSequenceNr)
      .map(
        pr =>
          EventEnvelope(
            offset = serializer.ordering(pr).fold(_ => Offset.noOffset, Offset.sequence),
            persistenceId = pr.persistenceId,
            sequenceNr = pr.sequence,
            event = pr.data,
            timestamp = serializer.timestamp(pr).getOrElse(0L)
          )
      )
  }

  private def adaptEvents(repr: PersistentRepr): Seq[PersistentRepr] = {
    val adapter = eventAdapters.get(repr.payload.getClass)
    adapter.fromJournal(repr.payload, repr.manifest).events.map(repr.withPayload)
  }

  override def currentEventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] = {
    val events =
      offset match {
        case NoOffset         => dao.eventsByTag(tag, 0)
        case Sequence(ordNr)  => dao.eventsByTag(tag, ordNr)
        case TimeBasedUUID(_) => ???
        case _                => ???
      }

    events
      .mapAsync(1)(row => Future.fromTry(serializer.deserialize(row)))
      .map(pr => EventEnvelope(offset, pr.persistenceId, pr.sequenceNr, pr.payload, pr.timestamp))
  }

  override def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] = {
    ???
  }

  override def persistenceIds(): Source[String, NotUsed] = {
    ???
  }

  override def eventsByPersistenceId(
    persistenceId:  String,
    fromSequenceNr: Long,
    toSequenceNr:   Long
  ): Source[EventEnvelope, NotUsed] = {
    ???
  }
}
