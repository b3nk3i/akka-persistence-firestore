package org.benkei.akka.persistence.firestore.serialization.extention

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.event.{Logging, LoggingAdapter}
import akka.serialization.SerializationExtension
import com.typesafe.config.Config
import org.benkei.akka.persistence.firestore.serialization.{BinaryPayloadSerializer, FirestorePayloadSerializer}

import scala.util.{Failure, Success, Try}

object FirestorePayloadSerializerExtension
    extends ExtensionId[FirestorePayloadSerializerExtension]
    with ExtensionIdProvider {

  override def lookup: FirestorePayloadSerializerExtension.type = FirestorePayloadSerializerExtension

  override def createExtension(system: ExtendedActorSystem) = new FirestorePayloadSerializerExtension(system)
}

class FirestorePayloadSerializerExtension(system: ExtendedActorSystem) extends Extension {

  private val log: LoggingAdapter = Logging(system, getClass)

  private val customPayloadSerializer: Try[FirestorePayloadSerializer] = {
    val fqcn = system.settings.config.getString("akka-persistence-firestore.payload-serializer-fqcn")
    val args = List(classOf[ExtendedActorSystem] -> system)
    system.dynamicAccess.createInstanceFor[FirestorePayloadSerializer](fqcn, args)
  }

  private val binaryPayloadSerializer: Try[FirestorePayloadSerializer] = {
    Try {
      new BinaryPayloadSerializer(SerializationExtension(system))
    }
  }

  def payloadSerializer(config: Config): FirestorePayloadSerializer = {
    customPayloadSerializer.orElse(binaryPayloadSerializer) match {
      case Success(result) =>
        log.info(s"Using ${result.getClass} FirestorePayloadSerializer.")
        result
      case Failure(t) =>
        throw new RuntimeException("Failed to create FirestorePayloadSerializer", t)
    }
  }

}
