package upmc.akka.leader

import java.util.Date
import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class Tick
case class CheckerTick () extends Tick

// CheckerActor is used to periodically check the liveness status of nodes and trigger a new election when a leader node failure is detected.
class CheckerActor(val id: Int, val terminals: List[Terminal], electionActor: ActorRef) extends Actor {
     val checkInterval: FiniteDuration = 500.milliseconds // interval for checking
     var musiciansAlive: Map[Int, Date] = Map() // stores nodes and their last active time
     var leader: Int = -1 // ID of the current leader
     val deathThreshold: Int = 3 // number of checks before a node is considered failed
     var terminationTask: Option[Cancellable] = None

     def receive: Receive = {
          case Start =>
               self ! CheckerTick

          case UpdateAlive(musicianId) =>
               // update the last active time of the node
               musiciansAlive += musicianId -> new Date
               if (musicianId != id && terminationTask.isDefined) {
                    terminationTask.foreach(_.cancel())
                    terminationTask = None
               }

          case UpdateAliveLeader(musicianId) =>
               // update the last active time of the leader and set the current leader
               musiciansAlive += musicianId -> new Date
               leader = musicianId
               if (musicianId != id && terminationTask.isDefined) {
                    terminationTask.foreach(_.cancel())
                    terminationTask = None
               }

          case CheckerTick =>
               // perform liveness check and schedule the next check
               performAliveCheck()
               scheduleNextCheck()
     }

     private def scheduleNextCheck(): Unit = {
          context.system.scheduler.scheduleOnce(checkInterval, self, CheckerTick)
     }

     private def performAliveCheck(): Unit = {
          val now = new Date
          musiciansAlive.foreach { case (musicianId, lastAlive) =>
               // if a node has not reported activity within the allowed time, consider it is left
               if (now.getTime - lastAlive.getTime > deathThreshold * checkInterval.toMillis) {
                    musiciansAlive -= musicianId
               if (musicianId == leader) {
                    // if the failed node is the leader, trigger a new election
                    triggerElection()
               } else {
                    context.parent ! Message(s"Musician $musicianId has quit")
               }
               }
          }
          if (musiciansAlive.size == 1 && musiciansAlive.keys.head == id && terminationTask.isEmpty) {
               terminationTask = Some(context.system.scheduler.scheduleOnce(30.seconds) {
                    if (musiciansAlive.size == 1 && musiciansAlive.keys.head == id) {
                         context.parent ! Message("Terminating system.")
                         context.system.terminate()
                    }
               })
          }
          context.parent ! UpdateNodesAlives(musiciansAlive.keys.toList)
     }

     private def triggerElection(): Unit = {
          leader = -1
          context.parent ! Message("LEADER has quit => ELECTION")
          // start a new election process
          electionActor ! StartElection(musiciansAlive.keys.toList)
     }
}
