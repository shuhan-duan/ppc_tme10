package upmc.akka.leader

import com.typesafe.config.ConfigFactory
import akka.actor._

case class Terminal (id:Int, ip:String, port:Int)

object Projet {

     // Permet de communiquer avec les autres nodes
     val config = List (
          Terminal (0, "127.0.0.1", 6000),
          Terminal (1, "127.0.0.1", 6001),
          Terminal (2, "127.0.0.1", 6002),
          Terminal (3, "127.0.0.1", 6003)
     )

     def main (args : Array[String]) {
          // Gestion des erreurs
          if (args.size != 1) {
               println ("Erreur de syntaxe : run <num>")
               sys.exit(1)
          }

          val id : Int = args(0).toInt

          if (id < 0 || id > 3) {
               println ("Errur : <num> doit etre compris entre 0 et 3")
               sys.exit(1)
          }

          // Initialisation du node <id>
          val system = ActorSystem("LeaderSystem" + id, ConfigFactory.load().getConfig("system" + id))
          val node = system.actorOf(Props(new Node(id, config)), "Node")

          node ! Start
     }

}
// package upmc.akka.ppc

// import akka.actor.{Props, Actor, ActorRef, ActorSystem}

// object Concert extends App {
//   println("Starting Mozart's game...")

//   val system = ActorSystem("MozartsGameSystem")

//   val dataBaseActor = system.actorOf(Props[DataBaseActor], "dataBaseActor")

//   val playerActor = system.actorOf(Props[PlayerActor], "playerActor")

//   val providerActor = system.actorOf(Props(new Provider(dataBaseActor)), "providerActor")

//   val conductorActor = system.actorOf(Props(new Conductor(providerActor, playerActor)), "conductorActor")

//   conductorActor ! StartGame
// }
