import sbt._

object Dependencies {

  object Versions {

    val cats = "2.4.2"
    val Firestore = "2.2.5"
    val Akka = "2.6.10"
  }

  object Libraries {
    val Cats = "org.typelevel" %% "cats-core" % Versions.cats
    val Firestore = "com.google.cloud" % "google-cloud-firestore" % Versions.Firestore
    val AkkaPersistence = "com.typesafe.akka" %% "akka-persistence" % Versions.Akka
    val AkkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % Versions.Akka % Test
  }

}