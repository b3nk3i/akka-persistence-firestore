import Dependencies._

name := "akka-persistence-firestore"

organization := "org.b3nk3i"

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

scalaVersion := "2.13.5"

lazy val root = (project in file("."))
  .settings(scalafmtOnCompile := true, libraryDependencies ++= Libraries.All)
  .configs(IntegrationTest.extend(Test))
  .settings(Defaults.itSettings)

homepage := Some(url("https://github.com/b3nk3i/akka-persistence-firestore"))

developers := List(Developer("b3nk3i", "Romain Petit", "rom1.petit@gmail.com", url("https://github.com/b3nk3i")))

// since february the server changed from "oss.sonatype.org" to "s01.oss.sonatype.org"
sonatypeCredentialHost := "s01.oss.sonatype.org"
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"