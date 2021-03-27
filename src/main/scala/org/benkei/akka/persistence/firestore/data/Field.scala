package org.benkei.akka.persistence.firestore.data

import com.google.cloud.firestore.Blob

sealed trait Field[-T] {
  def name: String
}

object Field {

  case object Ordering      extends Field[Long] { val name = "ordering" }
  case object Payload       extends Field[Blob] { val name = "payload" }
  case object Sequence      extends Field[Long] { val name = "sequence" }
  case object PersistenceID extends Field[String] { val name = "persistence-id" }
  case object Manifest      extends Field[String] { val name = "manifest" }
  case object Deleted       extends Field[Boolean] { val name = "deleted" }
  case object WriterUUID    extends Field[String] { val name = "writer-uuid" }
  case object SerializerID  extends Field[Long] { val name = "serializer-id" }
  case object Timestamp     extends Field[Long] { val name = "timestamp" }
  case object Tags          extends Field[java.util.List[String]] { val name = "tags" }

}
