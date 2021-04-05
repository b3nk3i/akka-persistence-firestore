package org.benkei.akka.persistence.firestore.serialization

import akka.actor.Actor
import akka.persistence.PersistentRepr
import akka.persistence.journal.Tagged
import akka.persistence.query.Offset
import org.benkei.akka.persistence.firestore.data.Document._
import org.benkei.akka.persistence.firestore.data.Field
import org.benkei.akka.persistence.firestore.journal.FirestorePersistentRepr

import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.Try

trait FirestoreSerializer {

  def serialize(pr: PersistentRepr): Try[FirestorePersistentRepr]

  def deserialize(fpr: FirestorePersistentRepr): Try[PersistentRepr]

  def ordering(fpr: FirestorePersistentRepr): Try[Offset]

  def timestamp(fpr: FirestorePersistentRepr): Try[Long]
}

object FirestoreSerializer {

  /*
    FirestoreSerializer implementation serialize the meta data and delegate payload related serialization to FirestorePayloadSerializer.
    FirestorePayloadSerializer handles Manifest,
   */
  def apply(serializer: FirestorePayloadSerializer): FirestoreSerializer =
    new FirestoreSerializer {

      def deserialize(fpr: FirestorePersistentRepr): Try[PersistentRepr] = {
        val data: Document = fpr.data
        for {
          sequence      <- data.read(Field.Sequence)
          persistenceId <- data.read(Field.PersistenceID)
          timestamp     <- timestamp(fpr)
          deleted       <- data.read(Field.Deleted)
          writerUUID    <- data.read(Field.WriterUUID)
          manifest      <- data.read(Field.Manifest)
          event         <- serializer.deserialize(manifest, data)
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
          p2         <- Try(updatedPr.payload.asInstanceOf[AnyRef])
          serPayload <- serializer.serialize(pr.manifest, p2)
        } yield {

          val serTags: List[String] = tags.toList

          val data: Document =
            updatedPr.deleted.write(Field.Deleted) ++
              updatedPr.persistenceId.write(Field.PersistenceID) ++
              updatedPr.sequenceNr.write(Field.Sequence) ++
              updatedPr.writerUuid.write(Field.WriterUUID) ++
              com.google.cloud.Timestamp.of(new java.sql.Timestamp(updatedPr.timestamp)).write(Field.Timestamp) ++
              serTags.asJava.write(Field.Tags) ++ serPayload

          FirestorePersistentRepr(updatedPr.persistenceId, updatedPr.sequenceNr, data)
        }

      }

      override def ordering(fpr: FirestorePersistentRepr): Try[Offset] = {
        fpr.data.read(Field.Ordering).map(UUID.fromString).map(Offset.timeBasedUUID)
      }

      override def timestamp(fpr: FirestorePersistentRepr): Try[Long] = {
        fpr.data.read(Field.Timestamp).map(_.toSqlTimestamp.getTime)
      }
    }
}
