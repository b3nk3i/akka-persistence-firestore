package org.benkei.akka.persistence.firestore.query
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
import org.benkei.akka.persistence.firestore.client.FireStoreExtension
import org.benkei.akka.persistence.firestore.config.{FirestoreJournalConfig, FirestoreReadJournalConfig}
import org.benkei.akka.persistence.firestore.journal.{FireStoreDao, FirestorePersistentRepr}
import org.benkei.akka.persistence.firestore.serialization.FirestoreSerializer
import org.benkei.akka.persistence.firestore.serialization.extention.FirestorePayloadSerializerExtension

import scala.collection.immutable.Set
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

  CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "closeReadJournalFirestore") { () =>
    Future(db.close()).map(_ => Done)
  }

  val journalConfig: FirestoreJournalConfig = FirestoreJournalConfig(config)

  val readJournalConfig: FirestoreReadJournalConfig = FirestoreReadJournalConfig(config)

  private val writePluginId = config.getString("write-plugin")

  // If 'config' is empty, or if the plugin reference is not found, then the write plugin will be resolved from the
  // ActorSystem configuration. Otherwise, it will be resolved from the provided 'config'.
  private val eventAdapters = Persistence(system).adaptersFor(writePluginId, config)

  private val delaySource =
    Source.tick(readJournalConfig.refreshInterval, 0.seconds, 0).take(1)

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
          offset = Offset.sequence(ordering),
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
    val events =
      offset match {
        case NoOffset         => dao.eventsByTag(tag, 0)
        case Sequence(ordNr)  => dao.eventsByTag(tag, ordNr)
        case TimeBasedUUID(_) => ???
        case _                => ???
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
  ): Source[EventEnvelope, NotUsed] = {

    def retrieveNextBatch(from: Offset) = {
      currentEventsByTag(tag, from)
        .mapAsync(1)(e => queue.offer(e).map(_ => e))
        .runWith(Sink.lastOption)
        .map(_.map(e => (e.offset, e)))
    }

    Source.unfoldAsync(offset) { from =>
      retrieveNextBatch(from)
        .flatMap {
          case Some((s, e)) => Future.successful(Some((s, e)))
          case None         => akka.pattern.after(readJournalConfig.refreshInterval, system.scheduler)(retrieveNextBatch(from))
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
              akka.pattern.after(readJournalConfig.refreshInterval, system.scheduler)(retrieveNextBatch(knownIds))
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

    if (fromSequenceNr > toSequenceNr) {
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
  ): Source[EventEnvelope, NotUsed] = {

    def retrieveNextBatch(from: Long) = {
      currentEventsByPersistenceId(persistenceId, from, toSequenceNr)
        .mapAsync(1)(e => queue.offer(e).map(_ => e))
        .runWith(Sink.lastOption)
        .map(_.map(e => (e.sequenceNr, e)))
    }

    Source.unfoldAsync(fromSequenceNr) { from =>
      retrieveNextBatch(from)
        .flatMap {
          case Some((s, _)) if s >= toSequenceNr =>
            // reached last offset - stop pulling & complete the queue
            queue.complete()
            Future.successful(None)
          case Some((s, e)) =>
            Future.successful(Some((s + 1, e))) // continue to pull
          case None =>
            akka.pattern.after(readJournalConfig.refreshInterval, system.scheduler)(retrieveNextBatch(from))
        }
    }
  }
}
