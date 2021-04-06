package org.benkei.akka.persistence.firestore.config

import com.typesafe.config.Config
import org.benkei.akka.persistence.firestore.config.ConfigOps.ConfigOperations

import scala.concurrent.duration.FiniteDuration

case class FirestoreReadJournalConfig(
  refreshInterval:          FiniteDuration,
  includeDeleted:           Boolean,
  maxBufferSize:            Int,
  eventualConsistencyDelay: FiniteDuration
)

object FirestoreReadJournalConfig {

  def apply(config: Config): FirestoreReadJournalConfig = {
    FirestoreReadJournalConfig(
      config.asFiniteDuration("refresh-interval"),
      config.getBoolean("includeLogicallyDeleted"),
      config.getInt("max-buffer-size"),
      config.asFiniteDuration("eventual-consistency-delay")
    )
  }
}
