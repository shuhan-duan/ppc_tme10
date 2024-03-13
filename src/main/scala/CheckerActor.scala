package upmc.akka.leader

import java.util.Date
import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class Tick
case class CheckerTick () extends Tick

// CheckerActor用于定期检查节点的存活状态，并在检测到领导者节点失败时触发新的选举。
class CheckerActor(val id: Int, val terminals: List[Terminal], electionActor: ActorRef) extends Actor {
     val checkInterval: FiniteDuration = 500.milliseconds // 检查间隔
     var musiciansAlive: Map[Int, Date] = Map() // 存储节点及其最后一次活跃时间
     var leader: Int = -1 // 当前领导者的ID
     val deathThreshold: Int = 3 // 节点被认为失效前的检查次数
     var terminationTask: Option[Cancellable] = None

     def receive: Receive = {
          case Start =>
               self ! CheckerTick

          case UpdateAlive(musicianId) =>
               // 更新节点的最后活跃时间
               musiciansAlive += musicianId -> new Date
               if (musicianId != id && terminationTask.isDefined) {
                    terminationTask.foreach(_.cancel())
                    terminationTask = None
               }

          case UpdateAliveLeader(musicianId) =>
               // 更新领导者的最后活跃时间，并设置当前领导者
               musiciansAlive += musicianId -> new Date
               leader = musicianId
               if (musicianId != id && terminationTask.isDefined) {
                    terminationTask.foreach(_.cancel())
                    terminationTask = None
               }

          case CheckerTick =>
               // 执行存活检查，并计划下一次检查
               performAliveCheck()
               scheduleNextCheck()
     }

     private def scheduleNextCheck(): Unit = {
          context.system.scheduler.scheduleOnce(checkInterval, self, CheckerTick)
     }

     private def performAliveCheck(): Unit = {
          val now = new Date
          musiciansAlive.foreach { case (musicianId, lastAlive) =>
               // 如果节点在允许的时间内未报告活跃，则认为节点失效
               if (now.getTime - lastAlive.getTime > deathThreshold * checkInterval.toMillis) {
                    musiciansAlive -= musicianId
               if (musicianId == leader) {
                    // 如果失效的是领导者节点，触发新的选举
                    triggerElection()
               } else {
                    context.parent ! Message(s"Musician $musicianId has quited")
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
          context.parent ! Message("LEADER has quited => ELECTION")
          // 启动新的选举过程
          electionActor ! StartElection(musiciansAlive.keys.toList)
     }
}

