package org.benkei.akka.persistence.firestore.journal

import akka.actor.Actor
import akka.persistence.PersistentRepr
import akka.persistence.journal.Tagged
import akka.serialization.{Serialization, Serializer, SerializerWithStringManifest, Serializers}

import scala.jdk.CollectionConverters._
import scala.util.Try

trait FirestoreSerializer {

  def serialize(pr: PersistentRepr): Try[FirestorePersistentRepr]

  def deserialize(fpr: FirestorePersistentRepr): Try[PersistentRepr]
}

object FirestoreSerializer {

  /*
    Default implementation based on Akka SerializerWithStringManifest.
   */
  def apply(serialization: Serialization): FirestoreSerializer =
    new FirestoreSerializer {

      def deserialize(fpr: FirestorePersistentRepr): Try[PersistentRepr] =
        Try {
          val data          = fpr.data
          val payload       = data("payload").asInstanceOf[String]
          val sequence      = data("sequence").asInstanceOf[Long]
          val persistenceId = data("persistence-id").asInstanceOf[String]
          val manifest      = data("manifest").asInstanceOf[String]
          val deleted       = data("deleted").asInstanceOf[Boolean]
          val writerUUID    = data("writer-uuid").asInstanceOf[String]
          val serializerId  = data("serializer-id").asInstanceOf[Int]

          val event = serialization.deserialize(payload.getBytes, serializerId, manifest)

          PersistentRepr(event, sequence, persistenceId, manifest, deleted, Actor.noSender, writerUUID)
        }

      def serialize(pr: PersistentRepr): Try[FirestorePersistentRepr] =
        Try {
          val (updatedPr, tags) = pr.payload match {
            case Tagged(payload, tags) => (pr.withPayload(payload), Map("tags" -> tags.toList.asJava))
            case _                     => (pr, Map.empty[String, Any])
          }

          val p2          = updatedPr.payload.asInstanceOf[AnyRef]
          val serializer  = serialization.findSerializerFor(p2)
          val serManifest = Serializers.manifestFor(serializer, p2)
          val serPayload  = serialization.serialize(p2)

          val data: Map[String, Any] =
            Map(
              "deleted"        -> updatedPr.deleted,
              "persistence-id" -> updatedPr.persistenceId,
              "sequence"       -> updatedPr.sequenceNr,
              "writer-uuid"    -> updatedPr.writerUuid,
              "timestamp"      -> updatedPr.timestamp,
              "manifest"       -> serManifest,
              "payload"        -> serPayload,
              "serializer-id"  -> serializer.identifier

              /*
              "meta-payload" -> serializedMetadata.map(_.payload),
              "meta-serializer-id" -> serializedMetadata.map(_.serId),
              "meta-manifest" -> serializedMetadata.map(_.serManifest)
               */
            ) ++ tags

          FirestorePersistentRepr(updatedPr.persistenceId, updatedPr.sequenceNr, data)
        }
    }
}
