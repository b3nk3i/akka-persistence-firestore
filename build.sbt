import Dependencies._

name := "akka-persistence-firestore "

version := "0.1"

scalaVersion := "2.13.5"

lazy val root = (project in file("."))
  .settings(scalafmtOnCompile := true, autoAPIMappings := true, libraryDependencies ++= Libraries.All)
