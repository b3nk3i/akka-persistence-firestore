package org.benkei.akka.persistence.firestore.journal

import akka.actor.Actor
import akka.persistence.PersistentRepr
import akka.persistence.journal.Tagged
import akka.serialization.{Serialization, Serializers}
import org.benkei.akka.persistence.firestore.data.Document._
import org.benkei.akka.persistence.firestore.data.Field

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
        val data: Document = fpr.data
        for {
          payload       <- data.read(Field.Payload)
          sequence      <- data.read(Field.Sequence)
          persistenceId <- data.read(Field.PersistenceID)
          manifest      <- data.read(Field.Manifest)
          deleted       <- data.read(Field.Deleted)
          writerUUID    <- data.read(Field.WriterUUID)
          serializerId  <- data.read(Field.SerializerID)
          event         <- serialization.deserialize(payload.getBytes, serializerId.toInt, manifest)
        } yield {
          PersistentRepr(event, sequence, persistenceId, manifest, deleted, Actor.noSender, writerUUID)
        }
      }

      def serialize(pr: PersistentRepr): Try[FirestorePersistentRepr] = {
        val (updatedPr, tags) = pr.payload match {
          case Tagged(payload, tags) => (pr.withPayload(payload), tags)
          case _                     => (pr, Set.empty)
        }

        for {
          p2          <- Try(updatedPr.payload.asInstanceOf[AnyRef])
          serializer  <- Try(serialization.findSerializerFor(p2))
          serManifest <- Try(Serializers.manifestFor(serializer, p2))
          serPayload  <- serialization.serialize(p2)
        } yield {
          val data: Document =
            updatedPr.deleted.write(Field.Deleted) ++
              updatedPr.persistenceId.write(Field.PersistenceID) ++
              updatedPr.sequenceNr.write(Field.Sequence) ++
              updatedPr.writerUuid.write(Field.WriterUUID) ++
              updatedPr.timestamp.write(Field.Timestamp) ++
              serManifest.write(Field.Manifest) ++
              new String(serPayload).write(Field.Payload) ++
              serializer.identifier.toLong.write(Field.SerializerID) ++
              tags.toList.write(Field.Tags)

          FirestorePersistentRepr(updatedPr.persistenceId, updatedPr.sequenceNr, data)
        }

      }
    }
}
