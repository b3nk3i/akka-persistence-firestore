package org.b3nk3i.akka.persistence.firestore.util

import java.io.InputStream
import scala.io.{Source => ScalaIOSource}

object ClasspathResources extends ClasspathResources

trait ClasspathResources {
  def streamToString(is: InputStream): String =
    ScalaIOSource.fromInputStream(is).mkString

  def fromClasspathAsString(fileName: String): String =
    streamToString(fromClasspathAsStream(fileName))

  def fromClasspathAsStream(fileName: String): InputStream =
    getClass.getClassLoader.getResourceAsStream(fileName)
}
