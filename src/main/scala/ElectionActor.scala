package upmc.akka.leader

import akka.actor._


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
               nodesAlive = List(id)  // reset nodesAlive
               leader = -1 // reset leader
               nodesAlive = nodesAlive ++ list
               initiateElection()
          }

          case Election(nominatorId) =>{
               father ! Message(s"I choose NEW LEADER is $nominatorId.")
               broadcastElectionResult(nominatorId)
          }
               
          case ElectionResult(newLeader) =>{
               if(leader != newLeader){
                    leader = newLeader
                    father ! LeaderChanged(leader)
               } 
          }
     }
     private def initiateElection(): Unit = {
          nodesAlive = nodesAlive.sorted
          val nominatorId = nodesAlive.max
          broadcastElection(nominatorId)
     }

     private def broadcastElection(nominatorId: Int): Unit = {
          if (nodesAlive.size == 1 && nodesAlive.head == id) {
               self ! Election(nominatorId) // 直接发送给自己
          } else {
               terminals.foreach { n =>
                    if (n.id != id) {
                         val remote = context.actorSelection(s"akka.tcp://LeaderSystem${n.id}@${n.ip}:${n.port}/user/Node/electionActor")
                         remote ! Election(nominatorId)
                    }
               }
          }
     }


     private def broadcastElectionResult(leader: Int): Unit = {
          if (nodesAlive.size == 1 && nodesAlive.head == id) {
               self ! ElectionResult(leader) 
          } else {
               terminals.foreach { n =>
                    if (n.id != id) {
                         val remote = context.actorSelection(s"akka.tcp://LeaderSystem${n.id}@${n.ip}:${n.port}/user/Node/electionActor")
                         remote ! ElectionResult(leader)
                    }
               }
          }
     }
}
