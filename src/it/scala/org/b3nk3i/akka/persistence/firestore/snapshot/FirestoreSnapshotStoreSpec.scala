package org.b3nk3i.akka.persistence.firestore.snapshot

import akka.persistence.snapshot.SnapshotStoreSpec
import com.dimafeng.testcontainers.{Container, ForAllTestContainer}
import com.typesafe.config.{Config, ConfigFactory}
import org.b3nk3i.akka.persistence.firestore.FirestoreJournalSpec.{ConfigBaseName, Port}
import org.b3nk3i.akka.persistence.firestore.emulator.FirestoreEmulator
import org.b3nk3i.akka.persistence.firestore.emulator.FirestoreEmulator.withFixedEmulator
import org.b3nk3i.akka.persistence.firestore.util.{ClasspathResources, FirestoreUtil}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.duration.DurationInt

abstract class AbstractFirestoreSnapshotStoreSpec(config: Config)
    extends SnapshotStoreSpec(config)
    with ForAllTestContainer
    with ClasspathResources
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ScalaFutures {

  override val container: Container = FirestoreEmulator.fixedfirestoreContainer(Port)

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 30.seconds)

  override def beforeEach(): Unit = {
    FirestoreUtil.clearCloudFirestore(config, "firestore-journal", system)
    super.beforeEach()
  }
}

object AbstractFirestoreSnapshotStoreSpec {
  val ConfigBaseName = "integration.conf"

  val Port = 9000
}

class FirestoreSnapshotStoreSpec
    extends AbstractFirestoreSnapshotStoreSpec(withFixedEmulator("localhost", Port, ConfigFactory.load(ConfigBaseName)))
