package upmc.akka.leader

import akka.actor._

// 启动消息
case object Start

// 存活消息类型
sealed trait AliveMessage
case class UpdateAlive(id: Int) extends AliveMessage
case class UpdateAliveLeader(id: Int) extends AliveMessage

// 节点类，负责初始化和管理各个子Actor，以及处理消息
class Node(val id: Int, val terminals: List[Terminal]) extends Actor {
     // 创建子Actor：选举、心跳和显示
     val leaderElectionActor = context.actorOf(Props(new LeaderElectionActor(id, terminals)), "leaderElectionActor")
     val beatActor = context.actorOf(Props(new BeatActor(id)), "beatActor")
     val displayActor = context.actorOf(Props[DisplayActor], "displayActor")

     def receive: Receive = {
          case Start => initialize()

          case Message(content) => displayActor ! Message(content)

          case BeatLeader => announceLeaderPresence()

          case Beat => announcePresence()

          case LeaderChanged(nodeId) => handleLeaderChange(nodeId)

          case UpdateAlive(nodeId) => updatePresence(nodeId)

          case UpdateAliveLeader(nodeId) => updateLeaderPresence(nodeId)
     }

     private def initialize(): Unit = {
          // 显示创建信息
          displayActor ! Message(s"Node $id is created")
          // 启动子Actor
          beatActor ! Start
          leaderElectionActor ! Start
          // 初始化与其他节点的通信路径
          initializeRemotes()
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
     }

     private def announceLeaderPresence(): Unit = {
          displayActor ! Message(s"I am alive $id and I am the LEADER!")
          broadcastToAll(UpdateAliveLeader(id))
     }

     private def updateLeaderPresence(nodeId: Int): Unit = {
          displayActor ! Message(s"The LEADER $nodeId is alive")
          leaderElectionActor ! UpdateAliveLeader(nodeId)
     }

     private def updatePresence(nodeId: Int): Unit = {
          displayActor ! Message(s"Node $nodeId is alive")
          leaderElectionActor ! UpdateAlive(nodeId)
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
