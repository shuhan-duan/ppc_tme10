package upmc.akka.leader

import akka.actor._


// 定义用于选举过程的消息
sealed trait ElectionMessage
case class StartElection(musicians: List[Int]) extends ElectionMessage
case class Election(id: Int) extends ElectionMessage
case class ElectionResult(newLeader: Int) extends ElectionMessage

class ElectionActor(val id: Int, val terminals: List[Terminal]) extends Actor {
     val father = context.parent
     var musiciansAlive: List[Int] = List(id)
     var leader: Int = -1 // 默认没有领导者

     def receive = {
          case StartElection(list) => {
               leader = -1 // reset leader
               musiciansAlive = list
               initiateElection()
          }

          case Election(nominatorId) =>{
               father ! Message(s"I choose NEW LEADER is $nominatorId.")
               broadcastElectionMessage(ElectionResult(nominatorId))
          }
               
          case ElectionResult(newLeader) =>{
               if(leader != newLeader){
                    leader = newLeader
                    father ! LeaderChanged(leader)
               } 
          }
     }
     private def initiateElection(): Unit = {
          musiciansAlive = musiciansAlive.sorted
          val nominatorId = musiciansAlive.max
          broadcastElectionMessage(Election(nominatorId))
     }

     private def broadcastElectionMessage(message: ElectionMessage): Unit = {
          if (musiciansAlive.size == 1 && musiciansAlive.head == id) {
               self ! message // 直接发送给自己
          } else {
               terminals.foreach { n =>
                    if (n.id != id) {
                         val cleanIp = n.ip.replaceAll("\"", "")
                         val remote = context.actorSelection(s"akka.tcp://MozartSystem${n.id}@${cleanIp}:${n.port}/user/Musician${n.id}/electionActor")
                         remote ! message
                    }
               }
          }
     }

}
