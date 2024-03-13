package upmc.akka.leader


import akka.actor.{Props, Actor, ActorRef}

class Provider(dataBaseActor: ActorRef) extends Actor {

  import DataBaseActor._

  var partie1 = Array.ofDim[Int](11, 8)
  partie1 = Array(Array(96, 22, 141, 41, 105, 122, 11, 30),
    Array(32, 6, 128, 63, 146, 46, 134, 81),
    Array(69, 95, 158, 13, 153, 55, 110, 24),
    Array(40, 17, 113, 85, 161, 2, 159, 100),
    Array(148, 74, 163, 45, 80, 97, 36, 107),
    Array(104, 157, 27, 167, 154, 68, 118, 91),
    Array(152, 60, 171, 53, 99, 133, 21, 127),
    Array(119, 84, 114, 50, 140, 86, 169, 94),
    Array(98, 142, 42, 156, 75, 129, 62, 123),
    Array(3, 87, 165, 61, 135, 47, 147, 33),
    Array(54, 130, 10, 103, 28, 37, 106, 5))

  var partie2 = Array.ofDim[Int](11, 8)
  partie2 = Array(Array(70, 121, 26, 9, 112, 49, 109, 14),
    Array(117, 39, 126, 56, 174, 18, 116, 83),
    Array(66, 139, 15, 132, 73, 58, 145, 79),
    Array(90, 176, 7, 34, 67, 160, 52, 170),
    Array(25, 143, 64, 125, 76, 136, 1, 93),
    Array(138, 71, 150, 29, 101, 162, 23, 151),
    Array(16, 155, 57, 175, 43, 168, 89, 172),
    Array(120, 88, 48, 166, 51, 115, 72, 111),
    Array(65, 77, 19, 82, 137, 38, 149, 8),
    Array(102, 4, 31, 164, 144, 59, 173, 78),
    Array(35, 20, 108, 92, 12, 124, 44, 131))

  var lastSender: ActorRef = _

  def receive = {
    case GetMeasureResult(result) =>
      lastSender = sender()
      val num = calculateMeasureNumber(result)
      dataBaseActor ! GetMeasure(num)

    case measure: Measure =>
      lastSender ! measure
  }

  var selectionCount = 0

  def calculateMeasureNumber(result: Int): Int = {
    val isPartie1 = selectionCount / 16 % 2 == 0
    val selectedPart = if (isPartie1) partie1 else partie2

    val arrayIndex = result - 2

    val elementIndex = selectionCount % 8
    selectionCount += 1

    selectedPart(arrayIndex)(elementIndex) - 1
  }


}
