package upmc.akka.leader

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.Random
import upmc.akka.leader.DataBaseActor.Measure

// Start message
case object Start

// Alive message types
sealed trait AliveMessage
case class UpdateAlive(id: Int) extends AliveMessage
case class UpdateAliveLeader(id: Int) extends AliveMessage
case class UpdateNodesAlives(musicians: List[Int]) extends AliveMessage

case object DeclareLeader

// Node class, responsible for initializing and managing various child actors, as well as handling messages
class Musician(val id: Int, val terminals: List[Terminal]) extends Actor {
     // Create child actors: election, checker, beat, and display
     val electionActor = context.actorOf(Props(new ElectionActor(id, terminals)), "electionActor")
     val checkerActor = context.actorOf(Props(new CheckerActor(id, terminals, electionActor)), "checkerActor")
     val beatActor = context.actorOf(Props(new BeatActor(id)), "beatActor")
     val displayActor = context.actorOf(Props[DisplayActor], "displayActor")

     val dataBaseActor = context.actorOf(Props[DataBaseActor], "dataBaseActor")
     val playerActor = context.actorOf(Props[PlayerActor], "playerActor")
     val providerActor = context.actorOf(Props(new Provider(dataBaseActor)), "providerActor")
     val conductorActor = context.actorOf(Props(new Conductor(providerActor, playerActor)), "conductorActor")

     var leader: Int = -1
     var musiciansAlive: List[Int] = List()
     var concertStarted: Boolean = false

     def receive: Receive = {

          case Start => initialize()

          case Message(content) => displayActor ! Message(content)

          case BeatLeader => announceLeaderPresence()

          case Beat => announcePresence()

          case UpdateAlive(musicianId) => updatePresence(musicianId)

          case UpdateAliveLeader(musicianId) => updateLeaderPresence(musicianId)

          case LeaderChanged(musicianId) => handleLeaderChange(musicianId)

          case DeclareLeader => declareLeader()

          case SendFromConductor(measure) => sendToPlayer(measure)

          case UpdateNodesAlives(list) => updateNodesAlives(list)
               
     }

     private def initialize(): Unit = {
          // Display creation message
          displayActor ! Message(s"Musician $id is created")
          // Start child actors
          checkerActor ! Start
          beatActor ! Start
          context.system.scheduler.scheduleOnce(4.seconds, self, DeclareLeader)(context.dispatcher)

     }

     private def announcePresence(): Unit = {
          displayActor ! Message(s"I am $id and I am in the concert")
          broadcastToAll(UpdateAlive(id))
          checkerActor ! UpdateAlive(id)
     }

     private def updatePresence(musicianId: Int): Unit = {
          displayActor ! Message(s"Musician $musicianId is in the concert")
          checkerActor ! UpdateAlive(musicianId)
     }

     private def announceLeaderPresence(): Unit = {
          displayActor ! Message(s"I am $id and I am the LEADER!")
          broadcastToAll(UpdateAliveLeader(id))
          checkerActor ! UpdateAliveLeader(id)
          if(musiciansAlive.size > 0 && !concertStarted) {
               displayActor ! Message(s"Concert Start")
               conductorActor ! StartConcert
               concertStarted = true
          }
     }

     private def updateLeaderPresence(musicianId: Int): Unit = {
          displayActor ! Message(s"The LEADER $musicianId is alive")
          leader = musicianId
          checkerActor ! UpdateAliveLeader(musicianId)
     }

     private def handleLeaderChange(musicianId: Int): Unit = {
          displayActor ! Message(s"The LEADER CHANGED it is now $musicianId")
          beatActor ! LeaderChanged(musicianId)
     }

     private def declareLeader(): Unit = {
          if (leader == -1) {
               leader = id
               displayActor ! Message(s"I am $id , I declare myself as the leader due to no other leader info received.")
               self ! BeatLeader
               beatActor ! LeaderChanged(id)
          }
     }

     private def broadcastToAll(message: AliveMessage): Unit = {
          terminals.filter(_.id != id).foreach { n =>
               val cleanIp = n.ip.replaceAll("\"", "")
               val address = s"akka.tcp://MozartSystem${n.id}@${cleanIp}:${n.port}/user/Musician${n.id}"
               val remote = context.actorSelection(address)
               remote ! message
          }
     }

     private def updateNodesAlives(musicians: List[Int]): Unit = {
          musiciansAlive = musicians.filter(_ != id)
     }

     private def sendToPlayer(measure: Measure): Unit = {
          if (musiciansAlive.nonEmpty) {
               // Select a random musician from musiciansAlive
               val playerId = musiciansAlive(Random.nextInt(musiciansAlive.size))
               displayActor ! Message(s"I choose Musician $id to play")
               val terminal = terminals.find(_.id == playerId).getOrElse(throw new IllegalArgumentException(s"Terminal with id $playerId not found"))
               val cleanIp = terminal.ip.replaceAll("\"", "")
               val remote = context.actorSelection(s"akka.tcp://MozartSystem${playerId}@${cleanIp}:${terminal.port}/user/Musician${playerId}/playerActor")
               remote ! measure
          } else {
               displayActor ! Message("No musicians alive to send measure.")
     }
}
     
}
