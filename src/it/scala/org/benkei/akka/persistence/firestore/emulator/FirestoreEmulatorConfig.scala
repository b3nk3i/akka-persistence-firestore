package org.benkei.akka.persistence.firestore.emulator

import com.typesafe.config.Config

case class FirestoreEmulatorConfig(
  dockerHost:    String,
  containerName: String,
  internalPort:  Int,
  hostPort:      Int,
  imageTag:      String
)

object FirestoreEmulatorConfig {

  def load(config: Config): FirestoreEmulatorConfig = {
    FirestoreEmulatorConfig(
      dockerHost = config.getString("firestore-emulator.docker-host"),
      containerName = config.getString("firestore-emulator.container-name"),
      internalPort = config.getInt("firestore-emulator.internal-port"),
      hostPort = config.getInt("firestore-emulator.host-port"),
      imageTag = config.getString("firestore-emulator.image-tag")
    )
  }
}
