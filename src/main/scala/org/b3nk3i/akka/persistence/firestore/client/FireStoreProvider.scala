package org.b3nk3i.akka.persistence.firestore.client

import akka.actor.ActorSystem
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.{Firestore, FirestoreOptions}
import com.typesafe.config.Config

trait FireStoreProvider {
  def client(config: Config): Firestore
}

class DefaultFireStoreProvider(system: ActorSystem) extends FireStoreProvider {
  override def client(config: Config): Firestore = {
    val projectId = config.getString("project-id")

    val options = FirestoreOptions
      .newBuilder()
      .setCredentials(GoogleCredentials.getApplicationDefault)
      .setProjectId(projectId)
      .build()

    options.getService
  }
}
