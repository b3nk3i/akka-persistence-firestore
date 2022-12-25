package org.b3nk3i.akka.persistence.firestore.config

import com.typesafe.config.Config
import org.b3nk3i.akka.persistence.firestore.config.ConfigOps.ConfigOperations

import scala.concurrent.duration.FiniteDuration

case class FirestoreSnapshotConfig(
  rootCollection: String,
  enqueueTimeout: FiniteDuration,
  queueSize:      Int,
  parallelism:    Int
)

object FirestoreSnapshotConfig {

  def apply(config: Config): FirestoreSnapshotConfig = {
    FirestoreSnapshotConfig(
      rootCollection = config.getString("root"),
      enqueueTimeout = config.asFiniteDuration("enqueue-timeout"),
      config.getInt("queue-size"),
      config.getInt("parallelism")
    )
  }
}
