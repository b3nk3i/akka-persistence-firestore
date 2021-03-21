import sbt._

object Config {
  // The It config extends Test to have access to libraries and classes defined in the Test scope
  val IT = config("it") extend Test
}
