package org.benkei.akka.persistence.firestore.data

sealed trait Field[-T] {
  def name: String
}

object Field {

  case object Payload       extends Field[String] { val name = "payload" }
  case object Sequence      extends Field[Long] { val name = "sequence" }
  case object PersistenceID extends Field[String] { val name = "persistence-id" }
  case object Manifest      extends Field[String] { val name = "manifest" }
  case object Deleted       extends Field[Boolean] { val name = "deleted" }
  case object WriterUUID    extends Field[String] { val name = "writer-uuid" }
  case object SerializerID  extends Field[Long] { val name = "serializer-id" }
  case object Timestamp     extends Field[Long] { val name = "timestamp" }
  case object Tags          extends Field[List[String]] { val name = "tags" }

}