import java.util.{concurrent => juc}
import com.ning.http.client.{ListenableFuture, AsyncHttpClient, AsyncHandler, Request}
import scala.concurrent.Promise
import scala.util.{Failure, Success}

package object dispatch {
  /** Type alias for RequestBuilder, our typical request definitions */
  type Req = com.ning.http.client.RequestBuilder
  /** Type alias for Response, avoid need to import */
  type Res = com.ning.http.client.Response
  /** Type alias for URI, avoid need to import */
  type Uri = java.net.URI

  /** type alias for dispatch future/ scala future **/
  type Future[+A] = scala.concurrent.Future[A]

  implicit val exec = scala.concurrent.ExecutionContext.Implicits.global

  implicit def implyRequestVerbs(builder: Req) =
    new DefaultRequestVerbs(builder)

  implicit def implyRequestHandlerTuple(builder: Req) =
    new RequestHandlerTupleBuilder(builder)

  implicit def implyRunnable[U](f: () => U) = new java.lang.Runnable {
    def run() { f() }
  }

  implicit def future2Enriched[T](f: Future[T]) = new EnrichedFuture[T](f)

  implicit def left2Future[A,B](f: PromiseEither.LeftProjection[A,B]): PromiseEither.EitherDelegate[A,B] =
    new PromiseEither.EitherDelegate[A,B](f.underlying)

  implicit def right2Future[A,B](f: PromiseEither.RightProjection[A,B]): PromiseEither.EitherDelegate[A,B] =
    new PromiseEither.EitherDelegate[A,B](f.underlying)


  implicit val durationOrdering = Ordering.by[Duration,Long] {
    _.millis
  }

  def toScalaFuture[T](
    listenableFuture: ListenableFuture[T],
    httpExecutor: HttpExecutor
  ): Future[T] = {
    val timeout = httpExecutor.timeout
    val promise = scala.concurrent.Promise[T]()

    listenableFuture.addListener(new Runnable {
      def run: Unit = promise.complete(
        try Success(listenableFuture.get(timeout.length, timeout.unit))
        catch {
          case e: Exception => Failure(e)
        }
      )
    }, httpExecutor.promiseExecutor)
    promise.future
  }
}
