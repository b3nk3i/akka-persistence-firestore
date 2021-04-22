package org.b3nk3i.akka.persistence.firestore.query

import akka.pattern.ask
import akka.persistence.query.{EventEnvelope, NoOffset, Offset}
import akka.stream.scaladsl.Source
import com.typesafe.config.{ConfigValue, ConfigValueFactory}
import org.b3nk3i.akka.persistence.firestore.internal.{TimeBasedUUIDs, UUIDTimestamp}
import org.b3nk3i.akka.persistence.firestore.query.CurrentEventsByTagTest._
import org.b3nk3i.akka.persistence.firestore.query.EventAdapterTest.{Event, TaggedAsyncEvent}

import scala.concurrent.duration._

object CurrentEventsByTagTest {
  val maxBufferSize: Int = 20

  val refreshInterval: FiniteDuration = 500.milliseconds

  val configOverrides: Map[String, ConfigValue] = Map(
    "firestore-read-journal.max-buffer-size"            -> ConfigValueFactory.fromAnyRef(maxBufferSize.toString),
    "firestore-read-journal.refresh-interval"           -> ConfigValueFactory.fromAnyRef(refreshInterval.toString),
    "firestore-read-journal.eventual-consistency-delay" -> ConfigValueFactory.fromAnyRef("0s")
  )
}

abstract class CurrentEventsByTagTest extends QueryTestSpec {

  it should "not find an event by tag for unknown tag" in withActorSystem(config, configOverrides) { implicit system =>
    val journalOps = new ScalaFirestoreReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "one")).futureValue
      (actor2 ? withTags(2, "two")).futureValue
      (actor3 ? withTags(3, "three")).futureValue

      eventually {
        val found = journalOps.countJournal.futureValue
        found shouldBe 3
      }

      journalOps.withCurrentEventsByTag()("unknown", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectComplete()
      }
    }
  }

  it should "find all events by tag" in withActorSystem(config, configOverrides) { implicit system =>
    val offset0: Offset = Offset.timeBasedUUID(TimeBasedUUIDs.create(UUIDTimestamp.now(), TimeBasedUUIDs.MinLSB))

    val journalOps = new ScalaFirestoreReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor2 ? withTags(2, "number")).futureValue
      (actor3 ? withTags(3, "number")).futureValue

      eventually {
        val found = journalOps.countJournal.futureValue
        found shouldBe 3
      }

      var offset1: Offset = NoOffset
      var offset2: Offset = NoOffset
      var offset3: Offset = NoOffset

      journalOps.withCurrentEventsByTag()("number", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(o1, "my-1", 1, 1) => offset1 = o1 }
        tp.expectNextPF { case EventEnvelope(o2, "my-2", 1, 2) => offset2 = o2 }
        tp.expectNextPF { case EventEnvelope(o3, "my-3", 1, 3) => offset3 = o3 }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("number", offset0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(_, "my-1", 1, 1) => }
        tp.expectNextPF { case EventEnvelope(_, "my-2", 1, 2) => }
        tp.expectNextPF { case EventEnvelope(_, "my-3", 1, 3) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("number", offset1) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(_, "my-2", 1, 2) => }
        tp.expectNextPF { case EventEnvelope(_, "my-3", 1, 3) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("number", offset2) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(_, "my-3", 1, 3) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("number", offset3) { tp =>
        tp.request(Int.MaxValue)
        tp.expectComplete()
      }
    }
  }

  it should "persist and find a tagged event with multiple tags" in withActorSystem(config, configOverrides) {
    implicit system =>
      val journalOps = new ScalaFirestoreReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
        withClue("Persisting multiple tagged events") {
          (actor1 ? withTags(1, "one", "1", "prime")).futureValue
          (actor1 ? withTags(2, "two", "2", "prime")).futureValue
          (actor1 ? withTags(3, "three", "3", "prime")).futureValue
          (actor1 ? withTags(4, "four", "4")).futureValue
          (actor1 ? withTags(5, "five", "5", "prime")).futureValue

          (actor2 ? withTags(3, "three", "3", "prime")).futureValue
          (actor3 ? withTags(3, "three", "3", "prime")).futureValue

          (actor1 ? 1).futureValue
          (actor1 ? 1).futureValue

          eventually {
            journalOps.countJournal.futureValue shouldBe 9
          }
        }

        journalOps.withCurrentEventsByTag()("one", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, "my-1", 1, 1) => }
          tp.expectComplete()
        }

        journalOps.withCurrentEventsByTag()("prime", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, "my-1", 1, 1) => }
          tp.expectNextPF { case EventEnvelope(_, "my-1", 2, 2) => }
          tp.expectNextPF { case EventEnvelope(_, "my-1", 3, 3) => }
          tp.expectNextPF { case EventEnvelope(_, "my-1", 5, 5) => }
          tp.expectNextPF { case EventEnvelope(_, "my-2", 1, 3) => }
          tp.expectNextPF { case EventEnvelope(_, "my-3", 1, 3) => }
          tp.expectComplete()
        }

        journalOps.withCurrentEventsByTag()("3", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, "my-1", 3, 3) => }
          tp.expectNextPF { case EventEnvelope(_, "my-2", 1, 3) => }
          tp.expectNextPF { case EventEnvelope(_, "my-3", 1, 3) => }
          tp.expectComplete()
        }

        journalOps.withCurrentEventsByTag()("4", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, "my-1", 4, 4) => }
          tp.expectComplete()
        }

        journalOps.withCurrentEventsByTag()("four", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, "my-1", 4, 4) => }
          tp.expectComplete()
        }

        journalOps.withCurrentEventsByTag()("5", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, "my-1", 5, 5) => }
          tp.expectComplete()
        }

        journalOps.withCurrentEventsByTag()("five", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, "my-1", 5, 5) => }
          tp.expectComplete()
        }
      }
  }

  it should "complete without any gaps in sequences when events are being persisted while the query is executed" in
    withActorSystem(config, configOverrides) { implicit system =>
      val journalOps = new JavaDslFirestoreReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
        def sendMessagesWithTag(tag: String, numberOfMessagesPerActor: Int) = {

          val actors = Seq(actor1, actor2, actor3)

          Source
            .fromIterator(() => (1 to numberOfMessagesPerActor).iterator)
            .flatMapConcat { i =>
              Source
                .fromIterator(() => actors.iterator.map((_, i)))
            }
            .mapAsync(actors.size) {
              case (actor, i) =>
                actor ? TaggedAsyncEvent(Event(i.toString), tag)
            }
        }

        val tag = "someTag"

        // send a batch of 3 * 200
        val batch1Size = 90
        val batch2Size = 300

        val batch1 = sendMessagesWithTag(tag, batch1Size / 3).run()

        // Try to persist a large batch of events per actor. Some of these may be returned, but not all!
        // Reduced for 5.0.0 as we can no longer do a batch insert due to the insert returning the ordering
        // so trying to persist 1000s in a batch is slower
        val batch2 = sendMessagesWithTag(tag, batch2Size / 3).run()

        batch1.futureValue

        // Sanity check, all events in the first batch must be in the journal
        journalOps.countJournal.futureValue should be >= batch1Size.toLong

        var batch2Offset: Offset = NoOffset

        // start a query before 2nd stream completes without Offset
        journalOps.withCurrentEventsByTag()(tag, NoOffset) { tp =>
          // The stream must complete within the given amount of time
          // This make take a while in case the journal sequence actor detects gaps
          val allEvents = tp.toStrict(atMost = 20.seconds)
          allEvents.size should be >= batch1Size

          batch2Offset = allEvents(batch1Size - 1).offset

          val zero: Map[String, Long] = Map("my-1" -> 0L, "my-2" -> 0L, "my-3" -> 0L)

          val stateBatch1 = allEvents.foldLeft(zero) {
            case (acc, EventEnvelope(_, pID, sequence, _)) =>
              acc(pID) + 1 shouldBe sequence
              acc + (pID -> sequence)
          }
          stateBatch1("my-1") should be >= (batch1Size / 3).toLong
          stateBatch1("my-2") should be >= (batch1Size / 3).toLong
          stateBatch1("my-3") should be >= (batch1Size / 3).toLong
        }

        // start a 2nd query before 2nd stream completes with Offset on last batch1
        journalOps.withCurrentEventsByTag()(tag, batch2Offset) { tp =>
          val batch2Events = tp.toStrict(atMost = 20.seconds)

          // state of each persistence id before 2nd query
          val state =
            batch2Events.collectFirst {
              case EventEnvelope(_, "my-1", sequence, _) => Map("my-1" -> (sequence - 1L))
            }.head ++
              batch2Events.collectFirst {
                case EventEnvelope(_, "my-2", sequence, _) => Map("my-2" -> (sequence - 1L))
              }.head ++
              batch2Events.collectFirst {
                case EventEnvelope(_, "my-3", sequence, _) => Map("my-3" -> (sequence - 1L))
              }.head

          val stateBatch2 = batch2Events.foldLeft(state) {
            case (acc, EventEnvelope(_, pID, sequence, _)) =>
              acc(pID) + 1 shouldBe sequence
              acc + (pID -> sequence)
          }

          stateBatch2.values.sum shouldBe batch1Size + batch2Events.size
        }
      }
    }
}
