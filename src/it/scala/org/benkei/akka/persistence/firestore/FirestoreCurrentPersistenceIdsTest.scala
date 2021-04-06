package org.benkei.akka.persistence.firestore

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.benkei.akka.persistence.firestore.FirestoreCurrentEventsByTagTest.ConfigBaseName
import org.benkei.akka.persistence.firestore.emulator.FirestoreEmulator
import org.benkei.akka.persistence.firestore.query.CurrentPersistenceIdsTest
import org.benkei.akka.persistence.firestore.util.FirestoreUtil

class FirestoreCurrentPersistenceIdsTest extends CurrentPersistenceIdsTest with FirestoreEmulator {

  lazy val config: Config = withEmulator(ConfigFactory.load(ConfigBaseName))

  override def beforeEach(): Unit = {
    val system = ActorSystem("test", config)
    FirestoreUtil.clearCloudFirestore(config, "firestore-journal", system)
    super.beforeEach()
  }
}

object FirestoreCurrentPersistenceIdsTest {
  val ConfigBaseName = "integration.conf"
}
