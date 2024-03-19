package upmc.akka.leader

import akka.actor.{Actor, ActorRef, Props}
import scala.concurrent.duration._
import upmc.akka.leader.DataBaseActor.Measure

case object StartConcert

case class GetMeasureResult(result: Int)
case class SendFromConductor(measure: Measure)

class Conductor(provider: ActorRef, player: ActorRef) extends Actor {

  import context.dispatcher
  import DataBaseActor._

  def receive = {
    case StartConcert =>
      val result = throwDice()
      provider ! GetMeasureResult(result)

    case measure: Measure =>
      context.parent ! SendFromConductor(measure)
      context.system.scheduler.scheduleOnce(1800.milliseconds, self, StartConcert)
  }

  private def throwDice(): Int = {
    val die1 = scala.util.Random.nextInt(6) + 1
    val die2 = scala.util.Random.nextInt(6) + 1
    die1 + die2
  }
}
