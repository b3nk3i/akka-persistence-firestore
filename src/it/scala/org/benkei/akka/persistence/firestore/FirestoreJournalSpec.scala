package org.benkei.akka.persistence.firestore

import com.typesafe.config.ConfigFactory
import org.benkei.akka.persistence.firestore.FirestoreJournalSpec.ConfigBaseName
import org.benkei.akka.persistence.firestore.util.FirestoreUtil

class FirestoreJournalSpec extends AbstractFirestoreJournalSpec(ConfigFactory.load(ConfigBaseName)) {

  override def beforeEach(): Unit = {
    FirestoreUtil.clearCloudFirestore(config, "firestore-journal", system)
    super.beforeEach()
  }
}

object FirestoreJournalSpec {
  val ConfigBaseName = "integration.conf"
}
