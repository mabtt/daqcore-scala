package scala.actors
package fix_daqcore

import scala.actors.scheduler.DaemonScheduler


/** Temporary fix/hack for the bugs in scala-2.8-beta1 Futures object
(at least Futures.alarm is broken). FuturesFixed contains the improved
implementation from scala-2.8-r21389. Hopefully will no longer be needed
once scala-2.8-beta2 (or whatever the next step will be) is released. */


/** The `Futures` object contains methods that operate on futures.
 *
 *  @author Philipp Haller
 */
object FuturesFixed {

  import scala.concurrent.SyncVar

  private case object Eval

  private class FutureActor[T](fun: SyncVar[T] => Unit, channel: Channel[T])
    extends Future[T](channel) with DaemonActor {

    import Actor._

    def isSet = !fvalue.isEmpty

    def apply(): T = {
      if (fvalue.isEmpty)
        this !? Eval
      fvalueTyped
    }

    def respond(k: T => Unit) {
      if (isSet) k(fvalueTyped)
      else {
        val ft = this !! Eval
        ft.inputChannel.react {
          case _ => k(fvalueTyped)
        }
      }
    }

    def act() {
      val res = new SyncVar[T]

      {
        fun(res)
      } andThen {
        fvalue =  Some(res.get)
        channel ! res.get
        loop {
          react {
            case Eval => reply()
          }
        }
      }
    }
  }

  /** Arranges for the asynchronous execution of `body`,
   *  returning a future representing the result.
   *
   *  @param  body the computation to be carried out asynchronously
   *  @return      the future representing the result of the
   *               computation
   */
  def future[T](body: => T): Future[T] = {
    val c = new Channel[T](Actor.self(DaemonScheduler))
    val a = new FutureActor[T](_.set(body), c)
    a.start()
    a
  }

  /** Creates a future that resolves after a given time span.
   *
   *  @param  timespan the time span in ms after which the future resolves
   *  @return          the future
   */
  def alarm(timespan: Long): Future[Unit] = {
    val c = new Channel[Unit](Actor.self(DaemonScheduler))
    val fun = (res: SyncVar[Unit]) => {
      Actor.reactWithin(timespan) {
        case TIMEOUT => res.set({})
      }
    }
    val a = new FutureActor[Unit](fun, c)
    a.start()
    a
  }

  /** Waits for the first result returned by one of two
   *  given futures.
   *
   *  @param  ft1 the first future
   *  @param  ft2 the second future
   *  @return the result of the future that resolves first
   */
  def awaitEither[A, B >: A](ft1: Future[A], ft2: Future[B]): B = {
    val FutCh1 = ft1.inputChannel
    val FutCh2 = ft2.inputChannel
    Actor.receive {
      case FutCh1 ! arg1 => arg1.asInstanceOf[B]
      case FutCh2 ! arg2 => arg2.asInstanceOf[B]
    }
  }

  /** Waits until either all futures are resolved or a given
   *  time span has passed. Results are collected in a list of
   *  options. The result of a future that resolved during the
   *  time span is its value wrapped in `Some`. The result of a
   *  future that did not resolve during the time span is `None`.
   *  
   *  Note that some of the futures might already have been awaited,
   *  in which case their value is returned wrapped in `Some`.
   *  Passing a timeout of 0 causes `awaitAll` to return immediately.
   *  
   *  @param  timeout the time span in ms after which waiting is
   *                  aborted
   *  @param  fts     the futures to be awaited
   *  @return         the list of optional future values
   *  @throws java.lang.IllegalArgumentException  if timeout is negative,
   *                  or timeout + `System.currentTimeMillis()` is negative.
   */
  def awaitAll(timeout: Long, fts: Future[Any]*): List[Option[Any]] = {
    var resultsMap: collection.mutable.Map[Int, Option[Any]] = new collection.mutable.HashMap[Int, Option[Any]]

    var cnt = 0
    val mappedFts = fts.map(ft =>
      Pair({cnt+=1; cnt-1}, ft))

    val unsetFts = mappedFts.filter((p: Pair[Int, Future[Any]]) => {
      if (p._2.isSet) { resultsMap(p._1) = Some(p._2()); false }
      else { resultsMap(p._1) = None; true }
    })

    val partFuns = unsetFts.map((p: Pair[Int, Future[Any]]) => {
      val FutCh = p._2.inputChannel
      val singleCase: PartialFunction[Any, Pair[Int, Any]] = {
        case FutCh ! any => Pair(p._1, any)
      }
      singleCase
    })

    val thisActor = Actor.self
    val timerTask = new java.util.TimerTask {
      def run() { thisActor ! TIMEOUT }
    }
    Actor.timer.schedule(timerTask, timeout)

    def awaitWith(partFuns: Seq[PartialFunction[Any, Pair[Int, Any]]]) {
      val reaction: PartialFunction[Any, Unit] = new PartialFunction[Any, Unit] {
        def isDefinedAt(msg: Any) = msg match {
          case TIMEOUT => true
          case _ => partFuns exists (_ isDefinedAt msg)
        }
        def apply(msg: Any): Unit = msg match {
          case TIMEOUT => // do nothing
          case _ => {
            val pfOpt = partFuns find (_ isDefinedAt msg)
            val pf = pfOpt.get // succeeds always
            val Pair(idx, subres) = pf(msg)
            resultsMap(idx) = Some(subres)

            val partFunsRest = partFuns filter (_ != pf)
            // wait on rest of partial functions
            if (partFunsRest.length > 0)
              awaitWith(partFunsRest)
          }
        }
      }
      Actor.receive(reaction)
    }

    if (partFuns.length > 0)
      awaitWith(partFuns)

    var results: List[Option[Any]] = Nil
    val size = resultsMap.size
    for (i <- 0 until size) {
      results = resultsMap(size - i - 1) :: results
    }

    // cancel scheduled timer task
    timerTask.cancel()

    results
  }

  private[actors] def fromInputChannel[T](inputChannel: InputChannel[T]): Future[T] =
    new Future[T](inputChannel) {
      def apply() =
        if (isSet) fvalueTyped
        else inputChannel.receive {
          case any => fvalue = Some(any); fvalueTyped
        }
      def respond(k: T => Unit): Unit =
        if (isSet) k(fvalueTyped)
        else inputChannel.react {
          case any => fvalue = Some(any); k(fvalueTyped)
        }
      def isSet = fvalue match {
        case None => inputChannel.receiveWithin(0) {
          case TIMEOUT => false
          case any => fvalue = Some(any); true
        }
        case Some(_) => true
      }
    }

}