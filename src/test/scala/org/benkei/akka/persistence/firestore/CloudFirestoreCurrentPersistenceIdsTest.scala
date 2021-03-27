package org.benkei.akka.persistence.firestore

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.benkei.akka.persistence.firestore.query.CurrentPersistenceIdsTest
import org.benkei.akka.persistence.firestore.util.FirestoreUtil

class CloudFirestoreCurrentPersistenceIdsTest extends CurrentPersistenceIdsTest("application.conf") {

  lazy val cfg: Config = ConfigFactory.load("application.conf")

  implicit lazy val system: ActorSystem = ActorSystem("test", cfg)

  override def beforeEach(): Unit = {
    FirestoreUtil.clearCloudFirestore(cfg, "firestore-journal", system)
    super.beforeEach()
  }
}
