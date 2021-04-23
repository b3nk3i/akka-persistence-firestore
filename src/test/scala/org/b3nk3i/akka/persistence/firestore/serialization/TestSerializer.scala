package org.b3nk3i.akka.persistence.firestore.serialization

import akka.actor.ExtendedActorSystem
import org.b3nk3i.akka.persistence.firestore.data.Field
import org.b3nk3i.akka.persistence.firestore.data.Document.Document

import scala.util.Try

class TestSerializer(system: ExtendedActorSystem) extends FirestorePayloadSerializer {

  def getManifest(payload: AnyRef): String = {
    payload match {
      case _: String => ""
      case _ => ""
    }
  }

  override def serialize(manifest: String, payload: AnyRef): Try[Document] =
    Try {
      val value =
        payload match {
          case e: String => e
          case o => sys.error(s"${o.getClass} not serializable")
        }

      Map(Field.SerializerID.name -> "[:]", Field.Manifest.name -> getManifest(payload), Field.Payload.name -> value)
    }

  override def deserialize(manifest: String, document: Document): Try[AnyRef] =
    Try {
      manifest match {
        case "" =>
          document(Field.Payload.name).asInstanceOf[String]
        case o =>
          sys.error(s"${o} not deserializable")
      }
    }
}
