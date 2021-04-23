package org.b3nk3i.akka.persistence.firestore

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.b3nk3i.akka.persistence.firestore.FirestoreCurrentEventsByTagTest.ConfigBaseName
import org.b3nk3i.akka.persistence.firestore.emulator.FirestoreEmulator
import org.b3nk3i.akka.persistence.firestore.query.PersistenceIdsTest
import org.b3nk3i.akka.persistence.firestore.util.FirestoreUtil

class FirestorePersistenceIdsTest extends PersistenceIdsTest with FirestoreEmulator {

  lazy val config: Config = withEmulator(ConfigFactory.load(ConfigBaseName))

  override def beforeEach(): Unit = {
    val system = ActorSystem("test", config)
    FirestoreUtil.clearCloudFirestore(config, "firestore-journal", system)
    super.beforeEach()
  }

}
