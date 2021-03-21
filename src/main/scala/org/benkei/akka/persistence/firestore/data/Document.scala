package org.benkei.akka.persistence.firestore.data

import scala.util.Try

object Document {

  type Document = Map[String, Any]

  implicit class DocumentReadOps(doc: Document) {
    def read[T](field: Field[T]): Try[T] = {
      Try(doc(field.name).asInstanceOf[T])
    }
  }

  implicit class DocumentWriteOps[T](value: T) {
    def write(field: Field[T]): Document = {
      Map(field.name -> value)
    }
  }
}
