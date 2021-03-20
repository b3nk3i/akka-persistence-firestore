import Dependencies._

name := "akka-persistence-firestore "

version := "0.1"

scalaVersion := "2.13.5"

lazy val root = (project in file("."))
  .settings(
    scalafmtOnCompile := true,
    autoAPIMappings := true,
    libraryDependencies ++= List(
      Libraries.Cats,
      Libraries.Firestore,
      Libraries.AkkaPersistence,
      Libraries.AkkaSlf4j
    )
  )