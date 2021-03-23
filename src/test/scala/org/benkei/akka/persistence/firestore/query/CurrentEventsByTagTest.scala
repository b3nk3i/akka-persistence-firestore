package org.benkei.akka.persistence.firestore.query

import akka.Done
import akka.pattern.ask
import akka.persistence.query.{EventEnvelope, NoOffset, Sequence}
import com.typesafe.config.{ConfigFactory, ConfigValue, ConfigValueFactory}
import org.benkei.akka.persistence.firestore.query.CurrentEventsByTagTest._
import org.benkei.akka.persistence.firestore.query.EventAdapterTest.{Event, TaggedAsyncEvent}

import scala.concurrent.Future
import scala.concurrent.duration._

object CurrentEventsByTagTest {
  val maxBufferSize: Int = 20

  val refreshInterval: FiniteDuration = 500.milliseconds

  val configOverrides: Map[String, ConfigValue] = Map(
    "jdbc-read-journal.max-buffer-size"  -> ConfigValueFactory.fromAnyRef(maxBufferSize.toString),
    "jdbc-read-journal.refresh-interval" -> ConfigValueFactory.fromAnyRef(refreshInterval.toString())
  )
}

abstract class CurrentEventsByTagTest(config: String) extends QueryTestSpec(config, configOverrides) {
  it should "not find an event by tag for unknown tag" in withActorSystem(ConfigFactory.load(config)) {
    implicit system =>
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

  it should "find all events by tag" in withActorSystem(ConfigFactory.load(config)) { implicit system =>
    val journalOps = new ScalaFirestoreReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor2 ? withTags(2, "number")).futureValue
      (actor3 ? withTags(3, "number")).futureValue

      eventually {
        val found = journalOps.countJournal.futureValue
        found shouldBe 3
      }

      journalOps.withCurrentEventsByTag()("number", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("number", Sequence(0)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("number", Sequence(1)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("number", Sequence(2)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("number", Sequence(3)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectComplete()
      }
    }
  }

  it should "persist and find a tagged event with multiple tags" in withActorSystem(ConfigFactory.load(config)) {
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
          tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, _) => }
          tp.expectComplete()
        }

        journalOps.withCurrentEventsByTag()("prime", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(5), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(6), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(7), _, _, _) => }
          tp.expectComplete()
        }

        journalOps.withCurrentEventsByTag()("3", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(6), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(7), _, _, _) => }
          tp.expectComplete()
        }

        journalOps.withCurrentEventsByTag()("4", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(Sequence(4), _, _, _) => }
          tp.expectComplete()
        }

        journalOps.withCurrentEventsByTag()("four", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(Sequence(4), _, _, _) => }
          tp.expectComplete()
        }

        journalOps.withCurrentEventsByTag()("5", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(Sequence(5), _, _, _) => }
          tp.expectComplete()
        }

        journalOps.withCurrentEventsByTag()("five", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(Sequence(5), _, _, _) => }
          tp.expectComplete()
        }
      }
  }

  it should "complete without any gaps in case events are being persisted when the query is executed" in withActorSystem(
    ConfigFactory.load(config)
  ) { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    import system.dispatcher
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      def sendMessagesWithTag(tag: String, numberOfMessagesPerActor: Int): Future[Done] = {
        val futures = for (actor <- Seq(actor1, actor2, actor3); i <- 1 to numberOfMessagesPerActor) yield {
          actor ? TaggedAsyncEvent(Event(i.toString), tag)
        }
        Future.sequence(futures).map(_ => Done)
      }

      val tag = "someTag"
      // send a batch of 3 * 200
      val batch1 = sendMessagesWithTag(tag, 200)
      // Try to persist a large batch of events per actor. Some of these may be returned, but not all!
      // Reduced for 5.0.0 as we can no longer do a batch insert due to the insert returning the ordering
      // so trying to persist 1000s in a batch is slower
      val batch2 = sendMessagesWithTag(tag, 2000)

      // wait for acknowledgement of the first batch only
      batch1.futureValue
      // Sanity check, all events in the first batch must be in the journal
      journalOps.countJournal.futureValue should be >= 600L

      // start the query before the last batch completes
      journalOps.withCurrentEventsByTag()(tag, NoOffset) { tp =>
        // The stream must complete within the given amount of time
        // This make take a while in case the journal sequence actor detects gaps
        val allEvents = tp.toStrict(atMost = 20.seconds)
        allEvents.size should be >= 600
        val expectedOffsets = 1L.to(allEvents.size).map(Sequence.apply)
        allEvents.map(_.offset) shouldBe expectedOffsets
      }
      batch2.futureValue
    }
  }
}
