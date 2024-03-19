package upmc.akka.leader

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor._

// Message types definition
sealed trait BeatMessage
case object Beat extends BeatMessage // Regular heartbeat message
case object BeatLeader extends BeatMessage // Leader heartbeat message

case object BeatTick // Heartbeat trigger message

case class LeaderChanged(musicianId: Int) // Leader change message, using Int directly

// The BeatActor is responsible for sending heartbeat messages periodically and updating the state when the leader changes.
class BeatActor(val id: Int) extends Actor {
     val beatInterval: FiniteDuration = 500.milliseconds // Define the heartbeat interval
     val father = context.parent // Parent Actor used for sending messages
     var leader: Int = -1

     def receive: Receive = {
          case Start => self ! BeatTick

          case BeatTick =>
               // Trigger heartbeat and schedule the next heartbeat
               triggerBeat()
               scheduleNextBeat()

          case LeaderChanged(musicianId) =>
               // Update the current leader ID and notify the parent Actor
               leader = musicianId
     }

     private def triggerBeat(): Unit = {
          // Send the corresponding heartbeat message based on whether the current is the leader
          if (id == leader) {
               father ! BeatLeader
          } else {
               father ! Beat
          }
     }

     private def scheduleNextBeat(): Unit = {
          // Use Akka's scheduler to schedule the next heartbeat
          context.system.scheduler.scheduleOnce(beatInterval, self, BeatTick)
     }
}