package org.benkei.akka.persistence.firestore.config

import com.typesafe.config.Config
import org.benkei.akka.persistence.firestore.config.ConfigOps.ConfigOperations

import scala.concurrent.duration.FiniteDuration

case class FirestoreJournalConfig(
  rootCollection: String,
  enqueueTimeout: FiniteDuration,
  queueSize:      Int,
  parallelism:    Int
)

object FirestoreJournalConfig {

  def apply(config: Config): FirestoreJournalConfig = {
    FirestoreJournalConfig(
      config.getString("root"),
      config.asFiniteDuration("enqueue-timeout"),
      config.getInt("queue-size"),
      config.getInt("parallelism")
    )
  }
}
