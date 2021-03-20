import sbt._

object Dependencies {

  object Versions {

    val Cats      = "2.4.2"
    val Firestore = "2.2.5"
    val Akka      = "2.6.10"
    val ScalaTest = "3.2.3"
  }

  object Libraries {
    val Cats      = "org.typelevel"   %% "cats-core"              % Versions.Cats
    val Firestore = "com.google.cloud" % "google-cloud-firestore" % Versions.Firestore

    val ScalaTest = "org.scalatest" %% "scalatest"       % Versions.ScalaTest % Test
    val Logback   = "ch.qos.logback" % "logback-classic" % "1.2.3"            % Test

    val Akka = List(
      "com.typesafe.akka" %% "akka-persistence"     % Versions.Akka,
      "com.typesafe.akka" %% "akka-slf4j"           % Versions.Akka % Test,
      "com.typesafe.akka" %% "akka-slf4j"           % Versions.Akka % Test,
      "com.typesafe.akka" %% "akka-persistence-tck" % Versions.Akka % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"  % Versions.Akka % Test,
      "com.typesafe.akka" %% "akka-testkit"         % Versions.Akka % Test
    )

    val All: List[ModuleID] = List(Cats, Firestore, ScalaTest, Logback) ++ Akka
  }
}
