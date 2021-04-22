package org.b3nk3i.akka.persistence.firestore.emulator

import com.dimafeng.testcontainers.{FixedHostPortGenericContainer, ForAllTestContainer, GenericContainer}
import com.typesafe.config.{Config, ConfigValue, ConfigValueFactory}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, TestSuite}
import org.slf4s.Logging
import org.testcontainers.containers.wait.strategy.Wait

trait FirestoreEmulator
    extends TestSuite
    with Matchers
    with BeforeAndAfterAll
    with ForAllTestContainer
    with Logging
    with Eventually
    with IntegrationPatience
    with ScalaFutures {

  override val container: GenericContainer = FirestoreEmulator.firestoreContainer()

  def withEmulator(config: Config): Config = FirestoreEmulator.withEmulator(container, config)
}

object FirestoreEmulator {

  def fixedfirestoreContainer(port: Int): FixedHostPortGenericContainer = {
    FixedHostPortGenericContainer(
      imageName = "ridedott/firestore-emulator:1.11.12",
      exposedHostPort = port,
      exposedContainerPort = 8080
    )
  }

  def firestoreContainer(): GenericContainer = {
    GenericContainer(
      dockerImage = "ridedott/firestore-emulator:1.11.12",
      exposedPorts = Seq(8080),
      waitStrategy = Wait.forHttp("/")
    )
  }

  def withEmulator(container: GenericContainer, config: Config): Config = {
    val configOverrides: Map[String, ConfigValue] = Map(
      FirestoreEmulatorConfig.EmulatorPort -> ConfigValueFactory.fromAnyRef(container.mappedPort(8080)),
      FirestoreEmulatorConfig.EmulatorHost -> ConfigValueFactory.fromAnyRef(container.containerIpAddress)
    )

    configOverrides.foldLeft(config) {
      case (conf, (path, configValue)) =>
        conf.withValue(path, configValue)
    }
  }

  def withFixedEmulator(host: String, port: Int, config: Config): Config = {
    val configOverrides: Map[String, ConfigValue] = Map(
      FirestoreEmulatorConfig.EmulatorPort -> ConfigValueFactory.fromAnyRef(port),
      FirestoreEmulatorConfig.EmulatorHost -> ConfigValueFactory.fromAnyRef(host)
    )

    configOverrides.foldLeft(config) {
      case (conf, (path, configValue)) =>
        conf.withValue(path, configValue)
    }
  }
}
