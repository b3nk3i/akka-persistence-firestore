package org.benkei.akka.persistence.firestore

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.benkei.akka.persistence.firestore.query.CurrentEventsByTagTest
import org.benkei.akka.persistence.firestore.util.FirestoreUtil

class CloudFirestoreCurrentPersistenceIdsTest extends CurrentEventsByTagTest("application.conf") {

  lazy val cfg: Config = ConfigFactory.load("application.conf")

  implicit lazy val system: ActorSystem = ActorSystem("test", cfg)

  def clearCloudFirestore(): Unit = {
    FirestoreUtil.clearCloudFirestore(cfg, "firestore-journal", system)
  }

  override def beforeEach(): Unit = {
    clearCloudFirestore()
    super.beforeEach()
  }
}
