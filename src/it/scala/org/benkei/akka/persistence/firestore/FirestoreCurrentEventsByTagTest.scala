package org.benkei.akka.persistence.firestore

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.benkei.akka.persistence.firestore.FirestoreCurrentEventsByTagTest.ConfigBaseName
import org.benkei.akka.persistence.firestore.emulator.FirestoreEmulator
import org.benkei.akka.persistence.firestore.query.CurrentEventsByTagTest
import org.benkei.akka.persistence.firestore.util.FirestoreUtil

class FirestoreCurrentEventsByTagTest extends CurrentEventsByTagTest(ConfigBaseName) with FirestoreEmulator {

  override lazy val config: Config = ConfigFactory.load(ConfigBaseName)

  implicit lazy val system = ActorSystem("test", config)

  override def beforeEach(): Unit = {
    FirestoreUtil.clearCloudFirestore(config, "firestore-journal", system)
    super.beforeEach()
  }
}

object FirestoreCurrentEventsByTagTest {
  val ConfigBaseName = "integration.conf"
}
