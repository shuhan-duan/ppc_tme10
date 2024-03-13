package upmc.akka.ppc

import akka.actor.{Actor, ActorRef, Props}
import scala.concurrent.duration._

case object StartConcert

case class GetMeasureResult(result: Int)

class Conductor(provider: ActorRef, player: ActorRef , val terminals: List[Terminal]) extends Actor {

    import context.dispatcher
    import DataBaseActor._

    def receive = {
      case StartConcert =>
        val result = throwDice()
        provider ! GetMeasureResult(result)

      case measure: Measure =>
      //to modifie  choose a player in nodesalive 
        player ! measure
        context.system.scheduler.scheduleOnce(1800.milliseconds, self, StartConcert)
    }


    private def throwDice(): Int = {
            val die1 = scala.util.Random.nextInt(6) + 1
            val die2 = scala.util.Random.nextInt(6) + 1
            die1 + die2
    }
}
