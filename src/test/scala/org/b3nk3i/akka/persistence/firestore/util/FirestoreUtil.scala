package org.b3nk3i.akka.persistence.firestore.util

import akka.actor.ActorSystem
import com.google.cloud.firestore.Firestore
import com.typesafe.config.Config
import org.b3nk3i.akka.persistence.firestore.client.FireStoreExtension
import org.b3nk3i.akka.persistence.firestore.config.FirestoreJournalConfig

import scala.jdk.CollectionConverters._

object FirestoreUtil {

  def clearCloudFirestore(cfg: Config, config: String, system: ActorSystem): Unit = {

    val firestoreJournalConfig = cfg.getConfig(config)

    val db: Firestore = FireStoreExtension(system).client(firestoreJournalConfig)

    val journalConfig: FirestoreJournalConfig = FirestoreJournalConfig(firestoreJournalConfig)

    db.collection(journalConfig.rootCollection)
      .listDocuments()
      .asScala
      .foreach { aggregateRoot =>
        aggregateRoot.delete().get()

        db.collection(journalConfig.rootCollection)
          .document(aggregateRoot.getId)
          .collection("event-journal")
          .listDocuments()
          .asScala
          .foreach { r => r.delete().get() }
      }

    db.close()
  }

}
