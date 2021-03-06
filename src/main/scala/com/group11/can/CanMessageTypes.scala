package com.group11.can

import akka.actor.{ActorRef}

object CanMessageTypes {
  case class JoinCan(shardRegionRef: ActorRef, existingNode:BigInt)
  case class RouteNewNode(p_x: Double, p_y: Double, newNode: BigInt)

  case class GetNodeId()
  case class GetNeighbors()
  case class GetCoord()
  case class SetCoord(l_x:Double,l_y:Double,u_x:Double,u_y:Double)

  case class AddNeighbor(lx:Double,ly:Double,ux:Double,uy:Double, nbrID: BigInt)
  case class RemoveNeighbor(nbrID: BigInt)
  case class UpdateNeighbor(nbrID: BigInt,lx:Double,ly:Double,ux:Double,uy:Double)

  case class JoinDone(message: String)
  case class RouteResponse(lx:Double,ly:Double,ux:Double,uy:Double)
  case class PrintNeighbors()

  case class WriteData(key:(Double,Double), value:Int, hops:Int)
  case class ReadData(key:(Double,Double), hops: Int)

  final case class EntityEnvelope(id: BigInt, payload: Any)

  case class GetStats()
}
