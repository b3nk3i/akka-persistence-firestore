package org.benkei.akka.persistence.firestore

import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalSpec
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.benkei.akka.persistence.firestore.util.FirestoreUtil
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.duration._

abstract class AbstractCloudFirestoreJournalSpec(config: Config)
    extends JournalSpec(config)
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ScalaFutures {

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = true

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 10.seconds)

  implicit lazy val ec = system.dispatcher

  override def beforeAll(): Unit = {
    FirestoreUtil.clearCloudFirestore(config, "firestore-journal", system)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }
}

class CloudFirestoreJournalSpec extends AbstractCloudFirestoreJournalSpec(ConfigFactory.load("application.conf"))

class CloudFirestoreJournalNativeSpec
    extends AbstractCloudFirestoreJournalSpec(
      ConfigFactory
        .load("application.conf")
        .withValue(
          "akka-persistence-firestore.payload-serializer-fqcn",
          ConfigValueFactory.fromAnyRef("org.benkei.akka.persistence.firestore.serialization.TestSerializer")
        )
    )
