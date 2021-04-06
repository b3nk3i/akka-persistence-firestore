package org.benkei.akka.persistence.firestore.query

import akka.pattern.ask
import akka.persistence.query.{EventEnvelope, NoOffset, Offset}
import akka.stream.scaladsl.Source
import com.typesafe.config.{ConfigValue, ConfigValueFactory}
import org.benkei.akka.persistence.firestore.internal.{TimeBasedUUIDs, UUIDTimestamp}
import org.benkei.akka.persistence.firestore.query.EventAdapterTest.{Event, EventRestored, TaggedAsyncEvent, TaggedEvent}
import org.benkei.akka.persistence.firestore.query.EventsByTagTest.{configOverrides, delayOverrides, refreshInterval}

import scala.concurrent.duration._

object EventsByTagTest {
  val maxBufferSize = 20

  val refreshInterval: FiniteDuration = 500.milliseconds

  val configOverrides: Map[String, ConfigValue] = Map(
    "firestore-read-journal.max-buffer-size"            -> ConfigValueFactory.fromAnyRef(maxBufferSize.toString),
    "firestore-read-journal.refresh-interval"           -> ConfigValueFactory.fromAnyRef(refreshInterval.toString),
    "firestore-read-journal.eventual-consistency-delay" -> ConfigValueFactory.fromAnyRef("0s")
  )

  def delayOverrides: Map[String, ConfigValue] = {
    configOverrides ++ Map("firestore-read-journal.eventual-consistency-delay" -> ConfigValueFactory.fromAnyRef("2s"))
  }
}

abstract class EventsByTagTest extends QueryTestSpec {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  final val NoMsgTime: FiniteDuration = 100.millis

  it should "not find events for unknown tags" in withActorSystem(config, configOverrides) { implicit system =>
    val journalOps = new ScalaFirestoreReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! withTags(1, "one")
      actor2 ! withTags(2, "two")
      actor3 ! withTags(3, "three")

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withEventsByTag()("unknown", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
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

      journalOps.withEventsByTag()("number", offset0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case e @ EventEnvelope(_, "my-1", 1, 1) => }
        tp.expectNextPF { case e @ EventEnvelope(_, "my-2", 1, 2) => }
        tp.expectNextPF { case e @ EventEnvelope(_, "my-3", 1, 3) => }
        tp.cancel()
      }

      var offset1: Offset = NoOffset
      var offset2: Offset = NoOffset
      var offset3: Offset = NoOffset

      journalOps.withEventsByTag()("number", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case e @ EventEnvelope(o1, "my-1", 1, 1) => ; offset1 = o1 }
        tp.expectNextPF { case e @ EventEnvelope(o2, "my-2", 1, 2) => ; offset2 = o2 }
        tp.expectNextPF { case e @ EventEnvelope(o3, "my-3", 1, 3) => ; offset3 = o3 }
        tp.cancel()
      }

      journalOps.withEventsByTag()("number", offset0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(offset1, "my-1", 1, 1, timestamp = 0L))
        tp.expectNext(EventEnvelope(offset2, "my-2", 1, 2, timestamp = 0L))
        tp.expectNext(EventEnvelope(offset3, "my-3", 1, 3, timestamp = 0L))
        tp.cancel()
      }

      journalOps.withEventsByTag()("number", offset1) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(offset2, "my-2", 1, 2, timestamp = 0L))
        tp.expectNext(EventEnvelope(offset3, "my-3", 1, 3, timestamp = 0L))
        tp.cancel()
      }

      journalOps.withEventsByTag()("number", offset2) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(offset3, "my-3", 1, 3, timestamp = 0L))
        tp.cancel()
      }

      journalOps.withEventsByTag()("number", offset3) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
        tp.expectNoMessage(NoMsgTime)
      }

      journalOps.withEventsByTag()("number", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case e @ EventEnvelope(_, "my-1", 1, 1) => }
        tp.expectNextPF { case e @ EventEnvelope(_, "my-2", 1, 2) => }
        tp.expectNextPF { case e @ EventEnvelope(_, "my-3", 1, 3) => }
        tp.expectNoMessage(NoMsgTime)

        actor1 ? withTags(1, "number")
        tp.expectNextPF { case e @ EventEnvelope(_, "my-1", 2, 1) => }

        actor1 ? withTags(1, "number")
        tp.expectNextPF { case e @ EventEnvelope(_, "my-1", 3, 1) => }

        actor1 ? withTags(1, "number")
        tp.expectNextPF { case e @ EventEnvelope(_, "my-1", 4, 1) => }
        tp.cancel()
        tp.expectNoMessage(NoMsgTime)
      }
    }
  }

  it should "deliver EventEnvelopes non-zero timestamps" in withActorSystem(config, configOverrides) {
    val offset0: Offset = Offset.timeBasedUUID(TimeBasedUUIDs.create(UUIDTimestamp.now(), TimeBasedUUIDs.MinLSB))

    implicit system =>
      val journalOps = new ScalaFirestoreReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
        val testStartTime = System.currentTimeMillis()

        (actor1 ? withTags(1, "number")).futureValue
        (actor2 ? withTags(2, "number")).futureValue
        (actor3 ? withTags(3, "number")).futureValue

        def assertTimestamp(timestamp: Long, clue: String) = {
          withClue(clue) {
            timestamp should !==(0L)
            // we want to prove that the event got a non-zero timestamp
            // but also a timestamp that between some boundaries around this test run
            (timestamp - testStartTime) should be < 120000L
            (timestamp - testStartTime) should be > 0L
          }
        }

        journalOps.withEventsByTag()("number", offset0) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF {
            case ev @ EventEnvelope(_, "my-1", 1, 1) =>
              assertTimestamp(ev.timestamp, "my-1")
          }
          tp.expectNextPF {
            case ev @ EventEnvelope(_, "my-2", 1, 2) =>
              assertTimestamp(ev.timestamp, "my-2")
          }
          tp.expectNextPF {
            case ev @ EventEnvelope(_, "my-3", 1, 3) =>
              assertTimestamp(ev.timestamp, "my-3")
          }
          tp.cancel()
        }
      }
  }

  it should "select events by tag with exact match" in withActorSystem(config, configOverrides) { implicit system =>
    val offset0: Offset = Offset.timeBasedUUID(TimeBasedUUIDs.create(UUIDTimestamp.now(), TimeBasedUUIDs.MinLSB))

    var offset1: Offset = NoOffset
    var offset2: Offset = NoOffset
    var offset3: Offset = NoOffset

    val journalOps = new ScalaFirestoreReadJournalOperations(system)

    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "number", "sharded-1")).futureValue
      (actor2 ? withTags(2, "number", "sharded-10")).futureValue
      (actor3 ? withTags(3, "number", "sharded-100")).futureValue

      journalOps.withEventsByTag()("number", offset0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case e @ EventEnvelope(o1, "my-1", 1, 1) => offset1 = o1 }
        tp.expectNextPF { case e @ EventEnvelope(o2, "my-2", 1, 2) => offset2 = o2 }
        tp.expectNextPF { case e @ EventEnvelope(o3, "my-3", 1, 3) => offset3 = o3 }
        tp.cancel()
      }

      journalOps.withEventsByTag()("sharded-1", offset0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(offset1, "my-1", 1, 1, timestamp = 0L))
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
      }

      journalOps.withEventsByTag()("sharded-10", offset0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(offset2, "my-2", 1, 2, timestamp = 0L))
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
      }

      journalOps.withEventsByTag()("sharded-100", offset0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(offset3, "my-3", 1, 3, timestamp = 0L))
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
      }
    }
  }

  /*
    eventual-consistency-delay is required.
   */
  it should "find all events by tag even when lots of events are persisted concurrently" in
    withActorSystem(config, delayOverrides) { implicit system =>
      val journalOps            = new ScalaFirestoreReadJournalOperations(system)
      val msgCountPerActor      = 20
      val numberOfActors        = 10
      val totalNumberOfMessages = msgCountPerActor * numberOfActors
      withManyTestActors(numberOfActors) { actors =>
        val actorsWithIndexes = actors.zipWithIndex
        for {
          messageNumber     <- 0 until msgCountPerActor
          (actor, actorIdx) <- actorsWithIndexes
        } actor ! TaggedEvent(Event(s"$actorIdx-$messageNumber"), "myEvent")

        journalOps.withEventsByTag()("myEvent", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          (1 to totalNumberOfMessages).foldLeft(Map.empty[Int, Int]) {
            case (map, idx) =>
              val eventRestored = tp.expectNext()
              val mgsParts      = eventRestored.event.asInstanceOf[EventRestored].value.split("-")
              val actorIdx      = mgsParts(0).toInt
              val msgNumber     = mgsParts(1).toInt
              val expectedCount = map.getOrElse(actorIdx, 0)

              assertResult(expected = expectedCount)(msgNumber)
              // keep track of the next message number we expect for this actor idx
              map.updated(actorIdx, msgNumber + 1)
          }
          tp.cancel()
          tp.expectNoMessage(NoMsgTime)
        }
      }
    }

  it should "find events by tag from an offset" in withActorSystem(config, configOverrides) { implicit system =>
    val offset0: Offset = Offset.timeBasedUUID(TimeBasedUUIDs.create(UUIDTimestamp.now(), TimeBasedUUIDs.MinLSB))
    var offset1: Offset = NoOffset
    var offset2: Offset = NoOffset
    var offset3: Offset = NoOffset

    val journalOps = new JavaDslFirestoreReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor2 ? withTags(2, "number")).futureValue
      (actor3 ? withTags(3, "number")).futureValue

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withEventsByTag()("number", offset0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(o1, "my-1", 1, 1) => offset1 = o1 }
        tp.expectNextPF { case EventEnvelope(o2, "my-2", 1, 2) => offset2 = o2 }
        tp.expectNextPF { case EventEnvelope(o3, "my-3", 1, 3) => offset3 = o3 }
        tp.cancel()
      }

      journalOps.withEventsByTag()("number", offset1) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(offset2, "my-2", 1, 2, timestamp = 0L))
        tp.expectNext(EventEnvelope(offset3, "my-3", 1, 3, timestamp = 0L))
        tp.expectNoMessage(NoMsgTime)

        actor1 ? withTags(1, "number")
        tp.expectNextPF { case EventEnvelope(_, "my-1", 2, 1) => }
        tp.cancel()
        tp.expectNoMessage(NoMsgTime)
      }
    }
  }

  it should "persist and find tagged event for one tag" in withActorSystem(config, configOverrides) { implicit system =>
    val journalOps = new JavaDslFirestoreReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      journalOps.withEventsByTag(10.seconds)("one", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNoMessage(NoMsgTime)

        actor1 ! withTags(1, "one") // 1
        tp.expectNextPF { case EventEnvelope(_, "my-1", 1, 1) => }
        tp.expectNoMessage(NoMsgTime)

        actor2 ! withTags(1, "one") // 2
        tp.expectNextPF { case EventEnvelope(_, "my-2", 1, 1) => }
        tp.expectNoMessage(NoMsgTime)

        actor3 ! withTags(1, "one") // 3
        tp.expectNextPF { case EventEnvelope(_, "my-3", 1, 1) => }
        tp.expectNoMessage(NoMsgTime)

        actor1 ! withTags(2, "two") // 4
        tp.expectNoMessage(NoMsgTime)

        actor2 ! withTags(2, "two") // 5
        tp.expectNoMessage(NoMsgTime)

        actor3 ! withTags(2, "two") // 6
        tp.expectNoMessage(NoMsgTime)

        actor1 ! withTags(1, "one") // 7
        tp.expectNextPF { case EventEnvelope(_, "my-1", 3, 1) => }
        tp.expectNoMessage(NoMsgTime)

        actor2 ! withTags(1, "one") // 8
        tp.expectNextPF { case EventEnvelope(_, "my-2", 3, 1) => }
        tp.expectNoMessage(NoMsgTime)

        actor3 ! withTags(1, "one") // 9
        tp.expectNextPF { case EventEnvelope(_, "my-3", 3, 1) => }
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
        tp.expectNoMessage(NoMsgTime)
      }
    }
  }

  it should "persist and find tagged events when stored with multiple tags" in
    withActorSystem(config, configOverrides) { implicit system =>
      val journalOps = new ScalaFirestoreReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
        (actor1 ? withTags(1, "one", "1", "prime")).futureValue
        (actor1 ? withTags(2, "two", "2", "prime")).futureValue
        (actor1 ? withTags(3, "three", "3", "prime")).futureValue
        (actor1 ? withTags(4, "four", "4")).futureValue
        (actor1 ? withTags(5, "five", "5", "prime")).futureValue
        (actor2 ? withTags(3, "three", "3", "prime")).futureValue
        (actor3 ? withTags(3, "three", "3", "prime")).futureValue

        (actor1 ? 6).futureValue
        (actor1 ? 7).futureValue
        (actor1 ? 8).futureValue
        (actor1 ? 9).futureValue
        (actor1 ? 10).futureValue

        eventually {
          journalOps.countJournal.futureValue shouldBe 12
        }

        journalOps.withEventsByTag(10.seconds)("prime", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, "my-1", 1, 1) => }
          tp.expectNextPF { case EventEnvelope(_, "my-1", 2, 2) => }
          tp.expectNextPF { case EventEnvelope(_, "my-1", 3, 3) => }
          tp.expectNextPF { case EventEnvelope(_, "my-1", 5, 5) => }
          tp.expectNextPF { case EventEnvelope(_, "my-2", 1, 3) => }
          tp.expectNextPF { case EventEnvelope(_, "my-3", 1, 3) => }
          tp.expectNoMessage(NoMsgTime)
          tp.cancel()
        }

        journalOps.withEventsByTag(10.seconds)("three", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, "my-1", 3, 3) => }
          tp.expectNextPF { case EventEnvelope(_, "my-2", 1, 3) => }
          tp.expectNextPF { case EventEnvelope(_, "my-3", 1, 3) => }
          tp.expectNoMessage(NoMsgTime)
          tp.cancel()
        }

        journalOps.withEventsByTag(10.seconds)("3", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, "my-1", 3, 3) => }
          tp.expectNextPF { case EventEnvelope(_, "my-2", 1, 3) => }
          tp.expectNextPF { case EventEnvelope(_, "my-3", 1, 3) => }
          tp.expectNoMessage(NoMsgTime)
          tp.cancel()
        }

        journalOps.withEventsByTag(10.seconds)("one", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, "my-1", 1, 1) => }
          tp.expectNoMessage(NoMsgTime)
          tp.cancel()
        }

        journalOps.withEventsByTag(10.seconds)("four", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, "my-1", 4, 4) => }
          tp.expectNoMessage(NoMsgTime)
          tp.cancel()
        }

        journalOps.withEventsByTag(10.seconds)("five", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, "my-1", 5, 5) => }
          tp.expectNoMessage(NoMsgTime)
          tp.cancel()
          tp.expectNoMessage(NoMsgTime)
        }
      }
    }

  def timeoutMultiplier: Int = 1

  it should "show the configured performance characteristics" in withActorSystem(config, configOverrides) {
    implicit system =>
      val journalOps = new ScalaFirestoreReadJournalOperations(system)

      withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
        def sendMessagesWithTag(tag: String, numberOfMessagesPerActor: Int) = {

          val actors = Seq(actor1, actor2, actor3)

          Source
            .fromIterator(() => (1 to numberOfMessagesPerActor).iterator)
            .flatMapConcat { i =>
              Source
                .fromIterator(() => actors.iterator)
                .mapAsync(actors.size) { actor =>
                  actor ? TaggedAsyncEvent(Event(i.toString), tag)
                }
            }
        }

        val actorNr = 3
        val batch1  = 10

        val tag1 = "someTag"
        // send a batch of 3 * 10
        sendMessagesWithTag(tag1, batch1).run()

        // start the query before the future completes
        journalOps.withEventsByTag()(tag1, NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextN(actorNr * batch1)

          tp.expectNoMessage(NoMsgTime)

          val batch2 = 5
          // Send a small batch of 3 * 5 messages
          sendMessagesWithTag(tag1, batch2).run()
          // Since queries are executed `refreshInterval`, there must be a small delay before this query gives a result
          tp.within(min = refreshInterval / 2, max = batch2 * actorNr * refreshInterval) {
            tp.expectNextN(actorNr * batch2)
          }
          tp.expectNoMessage(NoMsgTime)

          // another batch should be retrieved fast
          // send a second batch of 3 * 10
          val batch3 = 10

          sendMessagesWithTag(tag1, batch3).run()

          tp.within(min = refreshInterval / 2, max = batch3 * actorNr * refreshInterval) {
            tp.request(Int.MaxValue)
            tp.expectNextN(actorNr * batch3)
          }
          tp.expectNoMessage(NoMsgTime)
        }
      }
  }
}
