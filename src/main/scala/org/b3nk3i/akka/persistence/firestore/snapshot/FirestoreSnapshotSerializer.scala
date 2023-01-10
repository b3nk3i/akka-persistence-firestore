package org.b3nk3i.akka.persistence.firestore.snapshot

import akka.persistence.SnapshotMetadata
import org.b3nk3i.akka.persistence.firestore.data.Document._
import org.b3nk3i.akka.persistence.firestore.data.Field
import org.b3nk3i.akka.persistence.firestore.journal.FirestorePersistentRepr
import org.b3nk3i.akka.persistence.firestore.serialization.FirestorePayloadSerializer

import scala.util.Try

trait FirestoreSnapshotSerializer {

  def serialize(metadata: SnapshotMetadata, snapshot: Any): Try[FirestorePersistentRepr]

  def deserialize(fpr: FirestorePersistentRepr): Try[Any]

  def timestamp(fpr: FirestorePersistentRepr): Try[Long]

  def manifest(fpr: FirestorePersistentRepr): Try[String]
}

object FirestoreSnapshotSerializer {

  /*
    FirestoreSerializer implementation serialize the meta data and delegate payload related serialization to FirestorePayloadSerializer.
    FirestorePayloadSerializer handles Manifest,
   */
  def apply(serializer: FirestorePayloadSerializer): FirestoreSnapshotSerializer =
    new FirestoreSnapshotSerializer {

      def deserialize(fpr: FirestorePersistentRepr): Try[Any] = {
        serializer.deserialize(manifest(fpr).getOrElse(""), fpr.data)
      }

      def serialize(metadata: SnapshotMetadata, snapshot: Any): Try[FirestorePersistentRepr] = {

        for {
          p2         <- Try(snapshot.asInstanceOf[AnyRef])
          serPayload <- serializer.serialize("", p2)

        } yield {
          val data: Document =
            metadata.persistenceId.write(Field.PersistenceID) ++
              metadata.sequenceNr.write(Field.Sequence) ++
              com.google.cloud.Timestamp.of(new java.sql.Timestamp(metadata.timestamp)).write(Field.Timestamp) ++
              serPayload

          FirestorePersistentRepr(metadata.persistenceId, metadata.sequenceNr, data)
        }
      }

      override def timestamp(fpr: FirestorePersistentRepr): Try[Long] = {
        fpr.data.read(Field.Timestamp).map(_.toSqlTimestamp.getTime)
      }

      override def manifest(fpr: FirestorePersistentRepr): Try[String] = {
        fpr.data.read(Field.Manifest)
      }
    }
}
