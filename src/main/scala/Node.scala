package upmc.akka.leader

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

// 启动消息
case object Start

// 存活消息类型
sealed trait AliveMessage
case class UpdateAlive(id: Int) extends AliveMessage
case class UpdateAliveLeader(id: Int) extends AliveMessage

case object DeclareLeader

// 节点类，负责初始化和管理各个子Actor，以及处理消息
class Node(val id: Int, val terminals: List[Terminal]) extends Actor {
     // 创建子Actor：选举、检查器、心跳和显示
     val electionActor = context.actorOf(Props(new ElectionActor(id, terminals)), "electionActor")
     val checkerActor = context.actorOf(Props(new CheckerActor(id, terminals, electionActor)), "checkerActor")
     val beatActor = context.actorOf(Props(new BeatActor(id)), "beatActor")
     val displayActor = context.actorOf(Props[DisplayActor], "displayActor")

     var leader: Int = -1

     def receive: Receive = {

          case Start => initialize()

          case Message(content) => displayActor ! Message(content)

          case BeatLeader => announceLeaderPresence()

          case Beat => announcePresence()

          case UpdateAlive(nodeId) => updatePresence(nodeId)

          case UpdateAliveLeader(nodeId) => updateLeaderPresence(nodeId)

          case LeaderChanged(nodeId) => handleLeaderChange(nodeId)

          case DeclareLeader => declareLeader()
               
     }

     private def initialize(): Unit = {
          // 显示创建信息
          displayActor ! Message(s"Node $id is created")
          // 启动子Actor
          checkerActor ! Start
          beatActor ! Start
          // 初始化与其他节点的通信路径
          initializeRemotes()
          context.system.scheduler.scheduleOnce(4.seconds, self, DeclareLeader)(context.dispatcher)

     }

     private def initializeRemotes(): Unit = {
          terminals.filter(_.id != id).foreach { n =>
               val remote = context.actorSelection(s"akka.tcp://LeaderSystem${n.id}@${n.ip}:${n.port}/user/Node")
               displayActor ! Message(s"Initialized communication with Node ${n.id}")
          }
     }

     private def announcePresence(): Unit = {
          displayActor ! Message(s"I am alive $id")
          broadcastToAll(UpdateAlive(id))
          checkerActor ! UpdateAlive(id)
     }

     private def updatePresence(nodeId: Int): Unit = {
          displayActor ! Message(s"Node $nodeId is alive")
          checkerActor ! UpdateAlive(nodeId)
     }

     private def announceLeaderPresence(): Unit = {
          displayActor ! Message(s"I am alive $id and I am the LEADER!")
          broadcastToAll(UpdateAliveLeader(id))
          checkerActor ! UpdateAliveLeader(id)
     }

     private def updateLeaderPresence(nodeId: Int): Unit = {
          displayActor ! Message(s"The LEADER $nodeId is alive")
          leader = nodeId
          checkerActor ! UpdateAliveLeader(nodeId)
     }

     private def handleLeaderChange(nodeId: Int): Unit = {
          displayActor ! Message(s"The LEADER CHANGED it is now $nodeId")
          beatActor ! LeaderChanged(nodeId)
     }

     private def declareLeader(): Unit = {
          if (leader == -1) {
               leader = id
               displayActor ! Message(s"Node $id declares itself as the leader due to no heartbeat received.")
               self ! BeatLeader
               beatActor ! LeaderChanged(id)
          }
     }

     private def broadcastToAll(message: AliveMessage): Unit = {
          terminals.filter(_.id != id).foreach { n =>
               val remote = context.actorSelection(s"akka.tcp://LeaderSystem${n.id}@${n.ip}:${n.port}/user/Node")
               remote ! message
          }
     }
}
