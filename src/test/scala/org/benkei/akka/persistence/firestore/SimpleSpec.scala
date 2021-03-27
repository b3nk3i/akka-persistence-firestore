package org.benkei.akka.persistence.firestore

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.Config
import org.benkei.akka.persistence.firestore.util.ClasspathResources
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt

trait SimpleSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaFutures
    with TryValues
    with OptionValues
    with Eventually
    with ClasspathResources
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with GivenWhenThen {

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 1.minute)

  implicit val timeout: Timeout = Timeout(1.minute)

  /**
    * Sends the PoisonPill command to an actor and waits for it to die
    */
  def killActors(actors: ActorRef*)(implicit system: ActorSystem): Unit = {
    val tp = TestProbe()
    actors.foreach { (actor: ActorRef) =>
      tp.watch(actor)
      system.stop(actor)
      tp.expectTerminated(actor)
    }
  }

  def withActorSystem(config: Config)(f: ActorSystem => Unit): Unit = {
    implicit val system: ActorSystem = ActorSystem("test", config)
    f(system)
    system.terminate().futureValue
  }
}
