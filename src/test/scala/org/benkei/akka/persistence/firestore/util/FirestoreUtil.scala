package org.benkei.akka.persistence.firestore.util

import akka.actor.ActorSystem
import com.google.cloud.firestore.Firestore
import com.typesafe.config.Config
import org.benkei.akka.persistence.firestore.client.FireStoreExtension
import org.benkei.akka.persistence.firestore.config.FirestoreJournalConfig

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

    db.collection(journalConfig.rootCollection).document("sequences").create(Map("ordering" -> 0L).asJava).get()

    db.close()
  }

}