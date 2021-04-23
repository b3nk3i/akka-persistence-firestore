package org.b3nk3i.akka.persistence.firestore.emulator

import com.typesafe.config.Config

case class FirestoreEmulatorConfig(host: String, port: Int)

object FirestoreEmulatorConfig {

  val EmulatorPort = "firestore-emulator.port"
  val EmulatorHost = "firestore-emulator.host"

  def load(config: Config): FirestoreEmulatorConfig = {
    FirestoreEmulatorConfig(host = config.getString(EmulatorHost), port = config.getInt(EmulatorPort))
  }
}
