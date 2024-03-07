package upmc.akka.leader

import akka.actor._

// 定义节点的状态
sealed trait NodeStatus
case object Passive extends NodeStatus
case object Candidate extends NodeStatus
case object Waiting extends NodeStatus
case object Leader extends NodeStatus

// 定义用于选举过程的消息
sealed trait ElectionMessage
case class StartElection(nodes: List[Int]) extends ElectionMessage
case class Election(id: Int) extends ElectionMessage
case class ElectionResult(newLeader: Int) extends ElectionMessage

class ElectionActor(val id: Int, val terminals: List[Terminal]) extends Actor {
     val father = context.parent
     var nodesAlive: List[Int] = List(id)
     var leader: Int = -1 // 默认没有领导者

     def receive = {
     case StartElection(list) => {
          if (list.isEmpty) {
               this.nodesAlive = this.nodesAlive:::List(id)
          }
          else {
               this.nodesAlive = list ::: List(id)
          }
          initiateElection()
     }

     case Election(nominatorId) =>{
          if (this.leader < nominatorId) {
          this.leader = nominatorId
          father ! Message(s"I choose NEW LEADER is $nominatorId.")
          broadcastElectionResult(leader)
          }
     }
          
     case ElectionResult(newLeader) =>{
          this.leader = newLeader
          father ! LeaderChanged(newLeader)
     } 
     }

     private def initiateElection(): Unit = {
          nodesAlive = nodesAlive.sorted
          val nominatorId = nodesAlive.max
          broadcastElection(nominatorId)
     }

     private def broadcastElection(nominatorId: Int): Unit = {
          terminals.foreach { n =>
               if (n.id != id) {
               val remote = context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node/electionActor")
               remote ! Election(nominatorId)
               }
          }
     }

     private def broadcastElectionResult(leader: Int): Unit = {
          terminals.foreach { n =>
               if (n.id != id) {
               val remote = context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node/electionActor")
               remote ! ElectionResult(leader)
               }
          }
     }
}
