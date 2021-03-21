package org.benkei.akka.persistence.firestore.journal

import akka.actor.Actor
import akka.persistence.PersistentRepr
import akka.persistence.journal.Tagged
import akka.serialization.{Serialization, Serializers}

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

      def deserialize(fpr: FirestorePersistentRepr): Try[PersistentRepr] = {
        val data = fpr.data
        for {
          payload       <- Try(data("payload").asInstanceOf[String])
          sequence      <- Try(data("sequence").asInstanceOf[Long])
          persistenceId <- Try(data("persistence-id").asInstanceOf[String])
          manifest      <- Try(data("manifest").asInstanceOf[String])
          deleted       <- Try(data("deleted").asInstanceOf[Boolean])
          writerUUID    <- Try(data("writer-uuid").asInstanceOf[String])
          serializerId  <- Try(data("serializer-id").asInstanceOf[Long])
          event         <- serialization.deserialize(payload.getBytes, serializerId.toInt, manifest)
        } yield {
          PersistentRepr(event, sequence, persistenceId, manifest, deleted, Actor.noSender, writerUUID)
        }
      }

      def serialize(pr: PersistentRepr): Try[FirestorePersistentRepr] = {
        val (updatedPr, tags) = pr.payload match {
          case Tagged(payload, tags) => (pr.withPayload(payload), Map("tags" -> tags.toList.asJava))
          case _                     => (pr, Map.empty[String, Any])
        }

        for {
          p2          <- Try(updatedPr.payload.asInstanceOf[AnyRef])
          serializer  <- Try(serialization.findSerializerFor(p2))
          serManifest <- Try(Serializers.manifestFor(serializer, p2))
          serPayload  <- serialization.serialize(p2)
        } yield {
          val data: Map[String, Any] =
            Map(
              "deleted"        -> updatedPr.deleted,
              "persistence-id" -> updatedPr.persistenceId,
              "sequence"       -> updatedPr.sequenceNr,
              "writer-uuid"    -> updatedPr.writerUuid,
              "timestamp"      -> updatedPr.timestamp,
              "manifest"       -> serManifest,
              "payload"        -> new String(serPayload),
              "serializer-id"  -> serializer.identifier.toLong

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
}
