package upmc.akka.leader

import akka.actor._
import java.util.Date
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

// 定义消息类型
sealed trait ElectionMessage

case object CheckerTick
case class StartElection(nodes: List[Int]) extends ElectionMessage
case class Election(id: Int) extends ElectionMessage
case class ElectionResult(newLeader: Int) extends ElectionMessage

class LeaderElectionActor(val id: Int, val terminals: List[Terminal]) extends Actor {
    val father = context.parent
    val checkInterval: FiniteDuration = 500.milliseconds // 检查间隔
    var nodesAlive: Map[Int, Date] = Map() // 存储节点及其最后一次活跃时间
    var leader: Int = -1 // 当前领导者的ID
    val deathThreshold: Int = 3 // 节点被认为失效前的检查次数

    def receive: Receive = {
        case Start => 
             self ! CheckerTick
        
        case UpdateAlive(nodeId) =>
            // 更新其他节点的最后活跃时间
            nodesAlive += nodeId -> new Date

        case UpdateAliveLeader(nodeId) =>
            // 更新领导者的最后活跃时间，并设置当前领导者
            nodesAlive += nodeId -> new Date
            leader = nodeId

        case CheckerTick =>
            // 执行存活检查，并计划下一次检查
            performAliveCheck()
            scheduleNextCheck()

        case StartElection(_) =>
            initiateElection()

        case Election(nominatorId) =>
            if (leader < nominatorId) {
                leader = nominatorId
                father ! Message(s"I choose NEW LEADER is $nominatorId.")
                broadcastElectionResult(leader)
            }

        case ElectionResult(newLeader) =>
            leader = newLeader
            father ! LeaderChanged(newLeader)
    }

    private def scheduleNextCheck(): Unit = {
        context.system.scheduler.scheduleOnce(checkInterval, self, CheckerTick)
    }

    private def performAliveCheck(): Unit = {
        val now = new Date
        nodesAlive.foreach { case (nodeId, lastAlive) =>
            if (now.getTime - lastAlive.getTime > deathThreshold * checkInterval.toMillis) {
                nodesAlive -= nodeId
                if (nodeId == leader) {
                    // 如果失效的是领导者节点，触发新的选举
                    initiateElection()
                } else {
                    father ! Message(s"Node $nodeId is dead")
                }
            }
        }
    }

    private def initiateElection(): Unit = {
        leader = -1
        father ! Message("LEADER is dead => ELECTION")
        val aliveNodes = nodesAlive.keys.toList
        val nominatorId = if (aliveNodes.nonEmpty) aliveNodes.max else id
        broadcastElection(nominatorId)
    }

    private def broadcastElection(nominatorId: Int): Unit = {
        terminals.foreach { n =>
            val remote = context.actorSelection(s"akka.tcp://LeaderSystem${n.id}@${n.ip}:${n.port}/user/Node/leaderElectionActor")
            remote ! Election(nominatorId)
        }
    }

    private def broadcastElectionResult(leader: Int): Unit = {
        terminals.foreach { n =>
            val remote = context.actorSelection(s"akka.tcp://LeaderSystem${n.id}@${n.ip}:${n.port}/user/Node/leaderElectionActor")
            remote ! ElectionResult(leader)
        }
    }
}
