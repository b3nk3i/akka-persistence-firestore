import sbt._

object Dependencies {

  object Versions {

    val Cats      = "2.7.0"
    val Firestore = "3.1.0"
    val Akka      = "2.6.19"
    val ScalaTest = "3.2.12"

    val Logback = "1.2.5"
    val Slf4s   = "1.7.30.2"
  }

  object Libraries {
    val Cats      = "org.typelevel"   %% "cats-core"              % Versions.Cats
    val Firestore = "com.google.cloud" % "google-cloud-firestore" % Versions.Firestore

    val ScalaTest = "org.scalatest" %% "scalatest" % Versions.ScalaTest % Test
    val Logging =
      List(
        "ch.qos.logback"  % "logback-classic" % Versions.Logback % Test,
        "ch.timo-schmid" %% "slf4s-api"       % Versions.Slf4s
      )

    val Akka = List(
      "com.typesafe.akka" %% "akka-persistence"       % Versions.Akka,
      "com.typesafe.akka" %% "akka-persistence-query" % Versions.Akka,
      "com.typesafe.akka" %% "akka-slf4j"             % Versions.Akka % Test,
      "com.typesafe.akka" %% "akka-persistence-tck"   % Versions.Akka % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"    % Versions.Akka % Test,
      "com.typesafe.akka" %% "akka-testkit"           % Versions.Akka % Test
    )

    val Docker = List("com.dimafeng" %% "testcontainers-scala-scalatest" % "0.40.7" % Test)

    val All: List[ModuleID] = List(Cats, Firestore, ScalaTest) ++ Akka ++ Docker ++ Logging
  }
}
