package org.benkei.akka.persistence.firestore.emulator

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.typesafe.config.Config
import com.whisk.docker.impl.dockerjava.{Docker, DockerJavaExecutorFactory}
import com.whisk.docker.{DockerContainer, DockerFactory, DockerKit, DockerReadyChecker}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, TestSuite}
import org.slf4s.Logging

import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait FirestoreEmulator
    extends TestSuite
    with Matchers
    with BeforeAndAfterAll
    with DockerKit
    with Logging
    with Eventually
    with IntegrationPatience
    with ScalaFutures {

  sys.props.put("logback.configurationFile", "logback-it.xml")

  def config: Config

  val emulatorConfig: FirestoreEmulatorConfig = FirestoreEmulatorConfig.load(config)

  override val StartContainersTimeout: FiniteDuration = 30.seconds

  lazy val emulatorContainer: DockerContainer = DockerContainer(
    image = s"ridedott/firestore-emulator:${emulatorConfig.imageTag}",
    name = Some(emulatorConfig.containerName),
    hostname = Some(emulatorConfig.containerName)
  ).withPorts(emulatorConfig.internalPort -> Some(emulatorConfig.hostPort))
    .withReadyChecker(DockerReadyChecker.LogLineContains("Dev App Server is now running."))

  override val dockerContainers = List(emulatorContainer)

  override implicit val dockerFactory: DockerFactory = new DockerJavaExecutorFactory(
    new Docker(DefaultDockerClientConfig.createDefaultConfigBuilder.build)
  )

  override def beforeAll(): Unit = {
    super.beforeAll()
    log.info("Starting docker containers for integration tests")
    startAllOrFail()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    log.info("Stopping all containers")
    stopAllQuietly()
  }
}
