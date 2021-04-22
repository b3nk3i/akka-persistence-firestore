package org.b3nk3i.akka.persistence.firestore.serialization

import org.b3nk3i.akka.persistence.firestore.data.Document.Document

import scala.util.Try

/*
  Provides a mechanism to serialize the event in native Firestore format.
 */
trait FirestorePayloadSerializer {

  def serialize(manifest: String, payload: AnyRef): Try[Document]

  def deserialize(manifest: String, document: Document): Try[AnyRef]

}
