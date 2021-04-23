package org.b3nk3i.google

import akka.NotUsed
import akka.stream.QueueOfferResult.{Failure, QueueClosed}
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}
import akka.stream.{Materializer, OverflowStrategy}
import com.google.api.gax.rpc.ApiStreamObserver
import com.google.cloud.firestore.{DocumentSnapshot, Query}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

object FirestoreStreamingOps {

  val blocker: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  implicit class StreamQueryOps(val query: Query) {
    def toStream(max: Int, enqueueTimeout: Duration)(implicit mat: Materializer): Source[DocumentSnapshot, NotUsed] = {

      val (queue, source) =
        Source
          .queue[DocumentSnapshot](max, OverflowStrategy.backpressure)
          .preMaterialize()

      enqueueStream(query, queue, enqueueTimeout)
      source
    }
  }

  def enqueueStream(
    query:          Query,
    queue:          SourceQueueWithComplete[DocumentSnapshot],
    enqueueTimeout: Duration
  ): Future[Unit] = {
    Future(query.stream(observer(queue, enqueueTimeout)(blocker)))(blocker)
  }

  def observer[T](queue: SourceQueueWithComplete[T], timeout: Duration)(implicit
    ec:                  ExecutionContext
  ): ApiStreamObserver[T] = {

    new ApiStreamObserver[T] {
      override def onNext(value: T): Unit = {
        queue
          .offer(value)
          .flatMap {
            case Failure(cause) =>
              Future.failed(cause)
            case QueueClosed =>
              Future.failed(new RuntimeException(s"onNext($value) called when the stream has already completed."))
            case _ =>
              Future.unit
          }
          .unsafeRunSync(timeout)
      }

      override def onError(t: Throwable): Unit = {
        queue.fail(t)
      }

      override def onCompleted(): Unit = {
        queue.complete()
      }
    }
  }

  implicit class AwaitOps[A](f: Future[A]) {
    def unsafeRunSync(duration: Duration): A = {
      Await.result(f, duration)
    }
  }
}
