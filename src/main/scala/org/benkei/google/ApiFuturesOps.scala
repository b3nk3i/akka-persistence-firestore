package org.benkei.google

import com.google.api.core.{ApiFuture, ApiFutureCallback, ApiFutures}

import java.util.concurrent.Executor
import scala.concurrent.{Future, Promise}

object ApiFuturesOps {

  implicit class ApiFutureExt[T](apiFuture: ApiFuture[T]) {
    def futureLift(implicit ec: Executor): Future[T] = {
      val p = Promise[T]()
      ApiFutures.addCallback(
        apiFuture,
        new ApiFutureCallback[T] {
          def onFailure(t:      Throwable): Unit = p.failure(t)
          def onSuccess(result: T):         Unit = p.success(result)
        },
        ec
      )
      p.future
    }
  }
}
