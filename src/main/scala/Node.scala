package upmc.akka.leader

import akka.actor._

// 启动消息
case object Start

// 存活消息类型
sealed trait AliveMessage
case class IsAlive(id: Int) extends AliveMessage
case class IsAliveLeader(id: Int) extends AliveMessage

case object CanIBeLeader // 询问是否可以成为领导者
case class LeadershipResponse(canBeLeader: Boolean) // 对CanIBeLeader的响应

// 节点类，负责初始化和管理各个子Actor，以及处理消息
class Node(val id: Int, val terminals: List[Terminal]) extends Actor {
     // 创建子Actor：选举、检查器、心跳和显示
     val electionActor = context.actorOf(Props(new ElectionActor(id, terminals)), "electionActor")
     val checkerActor = context.actorOf(Props(new CheckerActor(id, terminals, electionActor)), "checkerActor")
     val beatActor = context.actorOf(Props(new BeatActor(id)), "beatActor")
     val displayActor = context.actorOf(Props[DisplayActor], "displayActor")

     // 在系统启动时执行的操作
     override def preStart(): Unit = {
     // 显示创建信息
     displayActor ! Message(s"Node $id is created")
     // 启动子Actor
     checkerActor ! Start
     beatActor ! Start
     // 初始化与其他节点的通信路径
     initializeRemotes()
     }

     def receive: Receive = {
          case Message(content) => displayActor ! Message(content)

          case BeatLeader(nodeId) => announceLeaderPresence(nodeId)

          case Beat(nodeId) => announcePresence(nodeId)

          case IsAlive(nodeId) => getPresence(nodeId)

          case IsAliveLeader(nodeId) => getLeaderPresence(nodeId)

          case LeaderChanged(nodeId) => handleLeaderChange(nodeId)
     }

     private def initializeRemotes(): Unit = {
     terminals.filter(_.id != id).foreach { n =>
          val remote = context.actorSelection(s"akka.tcp://LeaderSystem${n.id}@${n.ip}:${n.port}/user/Node")
          displayActor ! Message(s"Initialized communication with Node ${n.id}")
     }
     }

     private def announcePresence(nodeId: Int): Unit = {
          displayActor ! Message(s"I am alive $nodeId")
          broadcastToAll(IsAlive(id))
     }

     private def getPresence(nodeId: Int): Unit = {
          displayActor ! Message(s"Node $nodeId is alive")
          checkerActor ! IsAlive(nodeId)
     }

     private def announceLeaderPresence(nodeId: Int): Unit = {
     displayActor ! Message(s"I am alive $nodeId and I am the LEADER!")
     broadcastToAll(IsAliveLeader(id))
     }

     private def getLeaderPresence(nodeId: Int): Unit = {
     displayActor ! Message(s"The LEADER $nodeId is alive")
      checkerActor ! IsAliveLeader(nodeId)
     }

     private def handleLeaderChange(nodeId: Int): Unit = {
     displayActor ! Message(s"The LEADER CHANGED it is now $nodeId")
     beatActor ! LeaderChanged(nodeId)
     }

     private def broadcastToAll(message: AliveMessage): Unit = {
     terminals.filter(_.id != id).foreach { n =>
          val remote = context.actorSelection(s"akka.tcp://LeaderSystem${n.id}@${n.ip}:${n.port}/user/Node")
          remote ! message
     }
     }
}
