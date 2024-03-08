package upmc.akka.leader

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor._

// 消息类型定义
sealed trait BeatMessage
case object Beat extends BeatMessage // 普通心跳消息
case object BeatLeader extends BeatMessage // 领导者心跳消息

case object BeatTick // 心跳触发消息

case class LeaderChanged(nodeId: Int) // 领导者变更消息，直接使用Int

// 心跳Actor负责定时发送心跳消息，并在领导者变更时更新状态。
class BeatActor(val id: Int) extends Actor {
     val beatInterval: FiniteDuration = 500.milliseconds // 定义心跳间隔
     val father = context.parent // 父Actor，用于发送消息
     var leader: Int = 0 // 初始无领导者

     override def preStart(): Unit = {
          // 初始化时触发首次心跳
          self ! BeatTick
     }

     def receive: Receive = {
          case BeatTick =>
               // 触发心跳，然后重新安排下一次心跳
               triggerBeat()
               scheduleNextBeat()

          case LeaderChanged(nodeId) =>
               // 更新当前的领导者ID，并通知父Actor
               leader = nodeId
               father ! Message(s"New leader is $nodeId.")
     }

     private def triggerBeat(): Unit = {
          // 根据当前节点是否为领导者，发送相应的心跳消息
          if (id == leader) {
               father ! BeatLeader
          } else {
               father ! Beat
          }
     }

     private def scheduleNextBeat(): Unit = {
          // 使用Akka的调度器安排下一次心跳
          context.system.scheduler.scheduleOnce(beatInterval, self, BeatTick)
     }
}