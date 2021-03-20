package org.benkei.akka.persistence.firestore.client

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.google.cloud.firestore.Firestore
import com.typesafe.config.Config

import scala.util.{Failure, Success}

object FireStoreExtension extends ExtensionId[FireStoreExtensionImpl] with ExtensionIdProvider {
  override def lookup: FireStoreExtension.type = FireStoreExtension
  override def createExtension(system: ExtendedActorSystem) = new FireStoreExtensionImpl(system)
}

class FireStoreExtensionImpl(system: ExtendedActorSystem) extends Extension {

  private val firestoreProvider: FireStoreProvider = {
    val fqcn = system.settings.config.getString("akka-persistence-firestore.client-provider-fqcn")
    val args = List(classOf[ActorSystem] -> system)
    system.dynamicAccess.createInstanceFor[FireStoreProvider](fqcn, args) match {
      case Success(result) => result
      case Failure(t)      => throw new RuntimeException("Failed to create FireStoreProvider", t)
    }
  }

  def client(config: Config): Firestore = firestoreProvider.client(config)
}
