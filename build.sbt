import Dependencies._

name := "akka-persistence-firestore"

scalaVersion := "2.13.5"

inThisBuild(
  List(
    organization := "org.b3nk3i",
    homepage := Some(url("https://github.com/b3nk3i/akka-persistence-firestore")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/b3nk3i/akka-persistence-firestore"),
        "scm:git@github.com:b3nk3i/akka-persistence-firestore.git"
      )
    ),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(Developer("b3nk3i", "Romain Petit", "rom1.petit@gmail.com", url("https://github.com/b3nk3i"))),
    publishMavenStyle := true
  )
)

// The server changed from "oss.sonatype.org" to "s01.oss.sonatype.org"
sonatypeCredentialHost := "s01.oss.sonatype.org"
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
publishTo := sonatypePublishToBundle.value

lazy val root = (project in file("."))
  .settings(scalafmtOnCompile := true, libraryDependencies ++= Libraries.All)
  .configs(IntegrationTest.extend(Test))
  .settings(Defaults.itSettings)
