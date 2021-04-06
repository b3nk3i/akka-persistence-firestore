import Dependencies._

name := "akka-persistence-firestore "
organization := "org.benkei"
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.13.5"

lazy val root = (project in file("."))
  .settings(scalafmtOnCompile := true, autoAPIMappings := true, libraryDependencies ++= Libraries.All)
  .configs(IntegrationTest.extend(Test))
  .settings(Defaults.itSettings)
