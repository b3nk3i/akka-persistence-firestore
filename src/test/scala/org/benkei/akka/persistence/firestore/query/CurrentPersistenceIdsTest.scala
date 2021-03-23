package org.benkei.akka.persistence.firestore.query

import com.typesafe.config.ConfigFactory

abstract class CurrentPersistenceIdsTest(config: String) extends QueryTestSpec(config) {
  it should "not find any persistenceIds for empty journal" in withActorSystem(ConfigFactory.load(config)) {
    implicit system =>
      val journalOps = new ScalaFirestoreReadJournalOperations(system)
      journalOps.withCurrentPersistenceIds() { tp =>
        tp.request(1)
        tp.expectComplete()
      }
  }

  it should "find persistenceIds for actors" in withActorSystem(ConfigFactory.load(config)) { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! 1
      actor2 ! 1
      actor3 ! 1

      eventually {
        journalOps.withCurrentPersistenceIds() { tp =>
          tp.request(3)
          tp.expectNextUnordered("my-1", "my-2", "my-3")
          tp.expectComplete()
        }
      }
    }
  }
}
