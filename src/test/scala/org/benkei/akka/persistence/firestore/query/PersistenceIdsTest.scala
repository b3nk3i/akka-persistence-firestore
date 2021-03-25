package org.benkei.akka.persistence.firestore.query

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

abstract class PersistenceIdsTest(config: String) extends QueryTestSpec(config) {
  it should "not terminate the stream when there are not pids" in withActorSystem(ConfigFactory.load(config)) {
    implicit system =>
      val journalOps = new ScalaFirestoreReadJournalOperations(system)
      journalOps.withPersistenceIds() { tp =>
        tp.request(1)
        tp.expectNoMessage(100.millis)
        tp.cancel()
        tp.expectNoMessage(100.millis)
      }
  }

  it should "find persistenceIds for actors" in withActorSystem(ConfigFactory.load(config)) { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      journalOps.withPersistenceIds() { tp =>
        tp.request(10)
        tp.expectNoMessage(100.millis)

        actor1 ! 1
        tp.expectNext(ExpectNextTimeout, "my-1")
        tp.expectNoMessage(100.millis)

        actor2 ! 1
        tp.expectNext(ExpectNextTimeout, "my-2")
        tp.expectNoMessage(100.millis)

        actor3 ! 1
        tp.expectNext(ExpectNextTimeout, "my-3")
        tp.expectNoMessage(100.millis)

        actor1 ! 1
        tp.expectNoMessage(100.millis)

        actor2 ! 1
        tp.expectNoMessage(100.millis)

        actor3 ! 1
        tp.expectNoMessage(100.millis)

        tp.cancel()
        tp.expectNoMessage(100.millis)
      }
    }
  }
}
