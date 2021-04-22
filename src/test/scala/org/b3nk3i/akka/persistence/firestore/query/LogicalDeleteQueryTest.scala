package org.b3nk3i.akka.persistence.firestore.query

import akka.pattern._
import akka.persistence.query.{EventEnvelope, NoOffset}
import com.typesafe.config.{ConfigValue, ConfigValueFactory}
import org.b3nk3i.akka.persistence.firestore.query.LogicalDeleteQueryTest.configOverrides

import scala.concurrent.duration._

object LogicalDeleteQueryTest {

  val configOverrides: Map[String, ConfigValue] = Map(
    "firestore-read-journal.eventual-consistency-delay" -> ConfigValueFactory.fromAnyRef("0s")
  )
}

abstract class LogicalDeleteQueryTest extends QueryTestSpec {

  implicit val askTimeout: FiniteDuration = 500.millis

  it should "return logically deleted events when using CurrentEventsByTag (backward compatibility)" in
    withActorSystem(config, configOverrides) { implicit system =>
      val journalOps = new ScalaFirestoreReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, _, _) =>
        (actor1 ? withTags(1, "number")).futureValue
        (actor1 ? withTags(2, "number")).futureValue
        (actor1 ? withTags(3, "number")).futureValue

        // delete and wait for confirmation
        (actor1 ? DeleteCmd(1)).futureValue

        journalOps.withCurrentEventsByTag()("number", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, _, 1, 1) => }
          tp.expectNextPF { case EventEnvelope(_, _, 2, 2) => }
          tp.expectNextPF { case EventEnvelope(_, _, 3, 3) => }
          tp.expectComplete()
        }
      }
    }

  it should "return logically deleted events when using EventsByTag (backward compatibility)" in
    withActorSystem(config, configOverrides) { implicit system =>
      val journalOps = new ScalaFirestoreReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, _, _) =>
        (actor1 ? withTags(1, "number")).futureValue
        (actor1 ? withTags(2, "number")).futureValue
        (actor1 ? withTags(3, "number")).futureValue

        // delete and wait for confirmation
        (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"

        journalOps.withEventsByTag()("number", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, _, 1, 1) => }
          tp.expectNextPF { case EventEnvelope(_, _, 2, 2) => }
          tp.expectNextPF { case EventEnvelope(_, _, 3, 3) => }
          tp.cancel()
        }
      }
    }

  it should "return logically deleted events when using CurrentEventsByPersistenceId (backward compatibility)" in
    withActorSystem(config, configOverrides) { implicit system =>
      val journalOps = new ScalaFirestoreReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, _, _) =>
        (actor1 ? withTags(1, "number")).futureValue
        (actor1 ? withTags(2, "number")).futureValue
        (actor1 ? withTags(3, "number")).futureValue

        // delete and wait for confirmation
        (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"

        journalOps.withCurrentEventsByPersistenceId()("my-1") { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, _, 1, 1) => }
          tp.expectNextPF { case EventEnvelope(_, _, 2, 2) => }
          tp.expectNextPF { case EventEnvelope(_, _, 3, 3) => }
          tp.expectComplete()
        }
      }
    }

  it should "return logically deleted events when using EventsByPersistenceId (backward compatibility)" in
    withActorSystem(config, configOverrides) { implicit system =>
      val journalOps = new ScalaFirestoreReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, _, _) =>
        (actor1 ? withTags(1, "number")).futureValue
        (actor1 ? withTags(2, "number")).futureValue
        (actor1 ? withTags(3, "number")).futureValue

        // delete and wait for confirmation
        (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"

        journalOps.withEventsByPersistenceId()("my-1") { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(_, _, 1, 1) => }
          tp.expectNextPF { case EventEnvelope(_, _, 2, 2) => }
          tp.expectNextPF { case EventEnvelope(_, _, 3, 3) => }
          tp.cancel()
        }
      }
    }
}
