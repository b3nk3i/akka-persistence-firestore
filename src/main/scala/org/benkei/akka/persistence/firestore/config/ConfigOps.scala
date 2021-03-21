package org.benkei.akka.persistence.firestore.config

import com.typesafe.config.Config

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object ConfigOps {

  implicit class ConfigOperations(val config: Config) extends AnyVal {

    def asFiniteDuration(key: String): FiniteDuration =
      FiniteDuration(config.getDuration(key).toMillis, TimeUnit.MILLISECONDS)

  }
}
