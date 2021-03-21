package org.benkei.akka.persistence.firestore.client

import akka.actor.ActorSystem
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.cloud.firestore.{Firestore, FirestoreOptions}
import com.typesafe.config.Config
import org.benkei.akka.persistence.firestore.emulator.FirestoreEmulatorConfig

class FireStoreEmulatorProvider(system: ActorSystem)  extends FireStoreProvider {

  override def client(config: Config): Firestore = {
    val emulatorConfig = FirestoreEmulatorConfig.load(system.settings.config)

    val host = emulatorConfig.dockerHost
    val port = emulatorConfig.hostPort

    FirestoreOptions.newBuilder
      .setEmulatorHost(s"$host:$port")
      .setCredentialsProvider(FixedCredentialsProvider.create(new FirestoreOptions.EmulatorCredentials))
      .build()
      .getService
  }
}