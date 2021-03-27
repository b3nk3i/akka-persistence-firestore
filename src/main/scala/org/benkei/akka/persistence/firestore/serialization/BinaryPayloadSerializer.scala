package org.benkei.akka.persistence.firestore.serialization

import akka.serialization.{Serialization, Serializers}
import com.google.cloud.firestore.Blob
import org.benkei.akka.persistence.firestore.data.Document.{Document, _}
import org.benkei.akka.persistence.firestore.data.Field

import scala.util.Try

class BinaryPayloadSerializer(serialization: Serialization) extends FirestorePayloadSerializer {

  override def serialize(manifest: String, payload: AnyRef): Try[Document] = {
    for {
      serializer  <- Try(serialization.findSerializerFor(payload))
      serManifest <- Try(Serializers.manifestFor(serializer, payload))
      serPayload  <- serialization.serialize(payload)

    } yield {
      serializer.identifier.toLong.write(Field.SerializerID) ++
        serManifest.write(Field.Manifest) ++
        Blob.fromBytes(serPayload).write(Field.Payload)
    }
  }

  override def deserialize(manifest: String, document: Document): Try[AnyRef] = {
    for {
      serializerId <- document.read(Field.SerializerID)
      payload      <- document.read(Field.Payload)
      event        <- serialization.deserialize(payload.toBytes, serializerId.toInt, manifest)
    } yield event
  }
}
