package org.benkei.akka.persistence.firestore.serialization

import akka.actor.Actor
import akka.persistence.PersistentRepr
import akka.persistence.journal.Tagged
import akka.serialization.{Serialization, Serializers}
import com.google.cloud.firestore.Blob
import org.benkei.akka.persistence.firestore.data.Document._
import org.benkei.akka.persistence.firestore.data.Field
import org.benkei.akka.persistence.firestore.journal.FirestorePersistentRepr

import scala.jdk.CollectionConverters._
import scala.util.Try

trait FirestoreSerializer {

  def serialize(pr: PersistentRepr): Try[FirestorePersistentRepr]

  def deserialize(fpr: FirestorePersistentRepr): Try[PersistentRepr]

  def ordering(fpr: FirestorePersistentRepr): Try[Long]

  def timestamp(fpr: FirestorePersistentRepr): Try[Long]
}

object FirestoreSerializer {

  /*
    Default implementation based on Akka Serialization.
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
          timestamp     <- data.read(Field.Timestamp)
          deleted       <- data.read(Field.Deleted)
          writerUUID    <- data.read(Field.WriterUUID)
          serializerId  <- data.read(Field.SerializerID)
          event         <- serialization.deserialize(payload.toBytes, serializerId.toInt, manifest)
        } yield {
          PersistentRepr(event, sequence, persistenceId, manifest, deleted, Actor.noSender, writerUUID)
            .withTimestamp(timestamp)
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

          val serTags: List[String] = tags.toList

          val data: Document =
            updatedPr.deleted.write(Field.Deleted) ++
              updatedPr.persistenceId.write(Field.PersistenceID) ++
              updatedPr.sequenceNr.write(Field.Sequence) ++
              updatedPr.writerUuid.write(Field.WriterUUID) ++
              updatedPr.timestamp.write(Field.Timestamp) ++
              serManifest.write(Field.Manifest) ++
              Blob.fromBytes(serPayload).write(Field.Payload) ++
              serializer.identifier.toLong.write(Field.SerializerID) ++
              serTags.asJava.write(Field.Tags)

          FirestorePersistentRepr(updatedPr.persistenceId, updatedPr.sequenceNr, data)
        }

      }

      override def ordering(fpr: FirestorePersistentRepr): Try[Long] = {
        fpr.data.read(Field.Ordering)
      }

      override def timestamp(fpr: FirestorePersistentRepr): Try[Long] = {
        fpr.data.read(Field.Timestamp)
      }
    }
}
