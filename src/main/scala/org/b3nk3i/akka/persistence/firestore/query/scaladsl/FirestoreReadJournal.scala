package org.b3nk3i.akka.persistence.firestore.query
package scaladsl

import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.persistence.Persistence
import akka.persistence.query._
import akka.persistence.query.scaladsl._
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import akka.stream.{Materializer, OverflowStrategy, SystemMaterializer}
import akka.{Done, NotUsed}
import com.google.cloud.firestore.Firestore
import com.typesafe.config.Config
import org.b3nk3i.akka.persistence.firestore.client.FireStoreExtension
import org.b3nk3i.akka.persistence.firestore.config.{FirestoreJournalConfig, FirestoreReadJournalConfig}
import org.b3nk3i.akka.persistence.firestore.internal.{TimeBasedUUIDSerialization, TimeBasedUUIDs, UUIDTimestamp}
import org.b3nk3i.akka.persistence.firestore.journal.{FireStoreDao, FirestorePersistentRepr}
import org.b3nk3i.akka.persistence.firestore.query.scaladsl.FirestoreReadJournal.until
import org.b3nk3i.akka.persistence.firestore.serialization.FirestoreSerializer
import org.b3nk3i.akka.persistence.firestore.serialization.extention.FirestorePayloadSerializerExtension

import java.util.UUID
import scala.collection.immutable.Set
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.math.abs

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

  CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "closeReadJournalFirestore") { () =>
    Future(db.close()).map(_ => Done)
  }

  val journalConfig: FirestoreJournalConfig = FirestoreJournalConfig(config)

  val readJournalConfig: FirestoreReadJournalConfig = FirestoreReadJournalConfig(config)

  private val writePluginId = config.getString("write-plugin")

  // If 'config' is empty, or if the plugin reference is not found, then the write plugin will be resolved from the
  // ActorSystem configuration. Otherwise, it will be resolved from the provided 'config'.
  private val eventAdapters = Persistence(system).adaptersFor(writePluginId, config)

  val serializer: FirestoreSerializer = {
    FirestoreSerializer(FirestorePayloadSerializerExtension(system).payloadSerializer(config))
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

  private def eventEnvelope(pr: FirestorePersistentRepr) = {
    for {
      event    <- serializer.deserialize(pr)
      ordering <- serializer.ordering(pr)
    } yield {
      val adapter = eventAdapters.get(event.payload.getClass)
      adapter.fromJournal(event.payload, event.manifest).events.map { payload =>
        EventEnvelope(
          offset = ordering,
          persistenceId = event.persistenceId,
          sequenceNr = event.sequenceNr,
          event = payload,
          timestamp = event.timestamp
        )
      }
    }
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
      .map(eventEnvelope)
      .mapAsync(1) { Future.fromTry }
      .mapConcat[EventEnvelope](identity)
  }

  override def currentEventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] = {

    // Upper bound offset is delayed by eventualConsistencyDelay ms
    val to = until(readJournalConfig)

    val events =
      offset match {
        case NoOffset =>
          dao.eventsByTag(
            tag,
            TimeBasedUUIDSerialization.toSortableString(TimeBasedUUIDs.MinUUID),
            TimeBasedUUIDSerialization.toSortableString(to)
          )
        case TimeBasedUUID(uuid) =>
          dao.eventsByTag(
            tag,
            TimeBasedUUIDSerialization.toSortableString(uuid),
            TimeBasedUUIDSerialization.toSortableString(to)
          )
        case _ =>
          Source.failed(new IllegalArgumentException(s"Unsupported  ${offset} type."))
      }

    events
      .map(eventEnvelope)
      .mapAsync(1) { Future.fromTry }
      .mapConcat[EventEnvelope](identity)
  }

  override def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] = {

    val (queue, source) =
      Source
        .queue[EventEnvelope](readJournalConfig.maxBufferSize, OverflowStrategy.backpressure)
        .preMaterialize()

    currentEventsByTag(tag, offset)
      .mapAsync(1)(e => queue.offer(e).map(_ => e))
      .runWith(Sink.lastOption)
      .map {
        case Some(latest) => eventsByTagPublisher(queue, tag, latest.offset)
        case None         => eventsByTagPublisher(queue, tag, offset)
      }
      .foreach(_.run())

    source
  }

  def eventsByTagPublisher(
    queue:  SourceQueueWithComplete[EventEnvelope],
    tag:    String,
    offset: Offset
  ): Source[Unit, NotUsed] = {

    def retrieveNextBatch(from: Offset) = {
      currentEventsByTag(tag, from)
        .mapAsync(1)(e => queue.offer(e).map(_ => e))
        .runWith(Sink.lastOption)
        .map(_.map(e => (e.offset, ())))
    }

    Source.unfoldAsync(offset) { from =>
      retrieveNextBatch(from)
        .flatMap {
          case Some((s, _)) =>
            Future.successful(Some((s, ())))
          case None =>
            akka.pattern.after(readJournalConfig.refreshInterval, system.scheduler)(Future.successful(Some((from, ()))))
        }
    }
  }

  /*
    infinite pull of persistenceIds from journal
   */
  override def persistenceIds(): Source[String, NotUsed] = {

    val (queue, source) =
      Source
        .queue[String](readJournalConfig.maxBufferSize, OverflowStrategy.backpressure)
        .preMaterialize()

    val empty = Set.empty[String]

    def retrieveNextBatch(knownIds: Set[String]) = {
      currentPersistenceIds()
        .mapAsync(1) { id =>
          if (knownIds.contains(id)) Future.successful(id)
          else queue.offer(id).map(_ => id)
        }
        .fold(knownIds) { _ + _ }
        .map((_, ()))
        .runWith(Sink.lastOption)
    }

    Source
      .unfoldAsync(empty) { knownIds =>
        retrieveNextBatch(knownIds)
          .flatMap {
            case Some(ids) =>
              Future.successful(Some(ids))
            case None =>
              akka.pattern.after(readJournalConfig.refreshInterval, system.scheduler)(
                Future.successful(Some((knownIds, ())))
              )
          }
      }
      .run()

    source
  }

  override def eventsByPersistenceId(
    persistenceId:  String,
    fromSequenceNr: Long,
    toSequenceNr:   Long
  ): Source[EventEnvelope, NotUsed] = {

    val empty = Source.empty[EventEnvelope]

    if (Math.max(1, fromSequenceNr) > toSequenceNr) {
      empty
    } else {
      val (queue, source) =
        Source
          .queue[EventEnvelope](readJournalConfig.maxBufferSize, OverflowStrategy.backpressure)
          .preMaterialize()

      currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr)
        .mapAsync(1)(e => queue.offer(e).map(_ => e))
        .runWith(Sink.lastOption)
        .map {
          case Some(latest) =>
            if (latest.sequenceNr >= toSequenceNr) {
              // current events reached toSequenceNr, nothing more to enqueue
              queue.complete()
              empty
            } else {
              eventsByPersistenceIdPublisher(queue, persistenceId, latest.sequenceNr + 1, toSequenceNr)
            }
          case None =>
            eventsByPersistenceIdPublisher(queue, persistenceId, fromSequenceNr, toSequenceNr)
        }
        .foreach(_.run())

      source
    }
  }

  def eventsByPersistenceIdPublisher(
    queue:          SourceQueueWithComplete[EventEnvelope],
    persistenceId:  String,
    fromSequenceNr: Long,
    toSequenceNr:   Long
  ): Source[Unit, NotUsed] = {

    def retrieveNextBatch(from: Long) = {
      currentEventsByPersistenceId(persistenceId, from, toSequenceNr)
        .mapAsync(1)(e => queue.offer(e).map(_ => e))
        .runWith(Sink.lastOption)
        .map(_.map(e => (e.sequenceNr, None)))
    }

    Source.unfoldAsync[Long, Unit](fromSequenceNr) { from =>
      retrieveNextBatch(from)
        .flatMap {
          case Some((s, _)) if s >= toSequenceNr =>
            // reached last offset - stop pulling & complete the queue
            queue.complete()
            Future.successful(None)
          case Some((s, e)) =>
            Future.successful(Some((s + 1, ()))) // continue to pull
          case None =>
            akka.pattern.after(readJournalConfig.refreshInterval, system.scheduler)(Future.successful(Some((from, ()))))
        }
    }
  }
}

object FirestoreReadJournal {
  final val Identifier = "firestore-read-journal"

  def until(readJournalConfig: FirestoreReadJournalConfig): UUID = {
    val up = System.currentTimeMillis() - abs(readJournalConfig.eventualConsistencyDelay.toMillis)

    TimeBasedUUIDs.create(UUIDTimestamp.fromUnixTimestamp(up), TimeBasedUUIDs.MinLSB)
  }
}
