package org.benkei.akka.persistence.firestore

import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalSpec
import com.dimafeng.testcontainers.ForAllTestContainer
import com.typesafe.config.ConfigFactory
import org.benkei.akka.persistence.firestore.FirestoreJournalSpec.ConfigBaseName
import org.benkei.akka.persistence.firestore.emulator.FirestoreEmulator
import org.benkei.akka.persistence.firestore.emulator.FirestoreEmulator.withFixedEmulator
import org.benkei.akka.persistence.firestore.util.FirestoreUtil
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.duration.DurationInt

class FirestoreJournalSpec
    extends JournalSpec(withFixedEmulator("localhost", 8090, ConfigFactory.load(ConfigBaseName)))
    with ForAllTestContainer
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ScalaFutures {

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = true

  override val container = FirestoreEmulator.fixedfirestoreContainer(8090)

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 30.seconds)

  override def beforeEach(): Unit = {
    FirestoreUtil.clearCloudFirestore(config, "firestore-journal", system)
    super.beforeEach()
  }
}

object FirestoreJournalSpec {
  val ConfigBaseName = "integration.conf"
}
