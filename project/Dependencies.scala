import Config.IT
import sbt._

object Dependencies {

  object Versions {

    val Cats      = "2.4.2"
    val Firestore = "2.2.5"
    val Akka      = "2.6.10"
    val ScalaTest = "3.2.3"

    val DockerTestkit = "0.9.9"
    val DockerJava    = "3.2.7"

    val Logback = "1.2.3"
    val Slf4s   = "1.7.26"
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
      "com.typesafe.akka" %% "akka-persistence"     % Versions.Akka,
      "com.typesafe.akka" %% "akka-slf4j"           % Versions.Akka % Test,
      "com.typesafe.akka" %% "akka-slf4j"           % Versions.Akka % Test,
      "com.typesafe.akka" %% "akka-persistence-tck" % Versions.Akka % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"  % Versions.Akka % Test,
      "com.typesafe.akka" %% "akka-testkit"         % Versions.Akka % Test
    )

    val Docker = List(
      "com.whisk"             %% "docker-testkit-scalatest"        % Versions.DockerTestkit % IT,
      "com.whisk"             %% "docker-testkit-impl-docker-java" % Versions.DockerTestkit % IT,
      "com.github.docker-java" % "docker-java"                     % Versions.DockerJava    % IT
    )

    val All: List[ModuleID] = List(Cats, Firestore, ScalaTest) ++ Akka ++ Docker ++ Logging
  }
}
