package com.group11.hw3
import java.io.File

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{complete, concat, get, path, post, _}
import akka.util.Timeout
import com.google.gson.{GsonBuilder, JsonObject}
import com.group11.hw3.chord.ChordNode
import com.group11.hw3.utils.ChordUtils
import org.apache.commons.io.FileUtils

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object serverRedone {
  //private case class AdaptedResponse(message: String) extends ChordSystemCommand
  def apply(): Behavior[ChordSystemCommand] = Behaviors.setup { context =>
    var hashMap = new mutable.HashMap[BigInt,ActorRef[NodeCommand]]()
    val nodeList = new ListBuffer[BigInt]()
    var nodesInChord = new ListBuffer[ActorRef[NodeCommand]]()

    implicit val system:ActorSystem[NotUsed]=ActorSystem(Behaviors.empty,"http-server")
    implicit val executor: ExecutionContext = system.executionContext
    val r = new scala.util.Random
    implicit val timeout: Timeout = 3.seconds

    //Create nodes
    while (nodeList.size < NodeConstants.numNodes) {

      var hashID=ChordUtils.md5("N"+nodeList.size)
      if (!(nodeList.contains(hashID))) {
        nodeList += hashID
        var actRef = context.spawn(ChordNode(hashID), hashID.toString())
        Thread.sleep(10)
        hashMap.addOne(hashID, actRef)
      }
    }

    // Add nodes to Network
    // Assuming first node starts Network and is self references
    nodesInChord += hashMap(nodeList(0))
    for (i <- 1 until nodeList.size) {
      var randomNodeInNetwork = hashMap(nodeList(scala.util.Random.nextInt(i)))
      var joinStatus = ""
      def askNodeToJoinNetwork(ref:ActorRef[NodeCommand]) = JoinNetwork(ref,randomNodeInNetwork)
      context.ask(hashMap(nodeList(i)),askNodeToJoinNetwork) {
        case Success(JoinStatus(status)) =>
          joinStatus = status
          AdaptedResponse(status)
      }
      if (joinStatus == "Success") {
        context.log.info("Node {} joined the network",hashMap(nodeList(i)).toString)
        Thread.sleep(100)
      }
    }



    //Define and start http server
    val route = path("placeholder") {
      concat(
        get {
          parameters("name".as[String]) { (key) =>
            val index=r.nextInt(NodeConstants.numNodes)
            val nodeHash=nodeList(index)
            val x=hashMap.get(nodeHash)
            var msgReply = ""
            def buildRequest(ref:ActorRef[NodeCommand]) =
              getKeyValue(ref, key)

            val ref = x.head
//            x.head ! buildRequest
            context.ask(x.head,buildRequest)
            {
              case Success(HttpResponse(message)) => {
                msgReply = message
                AdaptedResponse(msgReply)
              }
              case Failure(_) => {
                msgReply = "Could not find Key : "+key
                AdaptedResponse(msgReply)
              }
            }
            complete("Read/Get response:"+msgReply)
          }
        },
        post {
          parameters("name".as[String],"val".as[String]) { (key,value) =>
            val index=r.nextInt(10)
            val nodeHash=nodeList(index)
            val x=hashMap.get(nodeHash)
            x.head ! writeKeyValue(key,value)
            complete("Post method done")
          }

        }
      )
    }
    val bindingFuture = Http().newServerAt("localhost", 9000).bind(route)

    /*
    println("**Received request for snapshot")
    val gson=new GsonBuilder().setPrettyPrinting().create()
    val nodesSnapshot = new JsonObject()
    var nodeSnap=new JsonObject()
    context.log.info("**Servicing snapshot request")
    for(node <- nodeList)
    {
      def buildRequestForSnapshot(ref:ActorRef[NodeCommand]) =
        GetNodeSnapshot(ref)

      val nodeRef=hashMap.get(node).head

      context.ask(nodeRef,buildRequestForSnapshot){
        case Success(GetNodeSnapshotResponse(snap:JsonObject))=> {
          println("Snapshot received for:"+nodeRef+" snap:"+snap)
          nodeSnap=snap
          nodesSnapshot.add("Node"+snap.get("Node"),snap)
          println(nodesSnapshot)
          val path2 = "/output/%s.json".format("/Snapshot/Nodes")
          FileUtils.write(new File(path2), gson.toJson(nodesSnapshot), "UTF-8")
          AdaptedResponse("Success")
        }
        case Failure(_) => {
          context.log.info("Failed to get snapshot form node")
          AdaptedResponse("Fail")
        }

      }

      println(nodesSnapshot)
    }
    //Thread.sleep(3000)
    println(nodesSnapshot)
//    val path2 = "output/%s.json".format("/Snapshot/Nodes")
//    FileUtils.write(new File(path2), gson.toJson(nodesSnapshot), "UTF-8")
      */
    Behaviors.receiveMessage[ChordSystemCommand] {

      case UpdateFingerTables() =>
        Behaviors.same

      case WriteInitialData() =>
        Behaviors.same

      case CaptureGlobalSnapshot() =>
        println("**Received request for snapshot")
        val gson=new GsonBuilder().setPrettyPrinting().create()
        val nodesSnapshot = new JsonObject()
        var nodeSnap=new JsonObject()
        context.log.info("**Servicing snapshot request")
        for(node <- nodeList)
        {
          def buildRequestForSnapshot(ref:ActorRef[NodeCommand]) =
            GetNodeSnapshot(ref)

          val nodeRef=hashMap.get(node).head

          context.ask(nodeRef,buildRequestForSnapshot){
            case Success(GetNodeSnapshotResponse(snap:JsonObject))=> {
              println("Snapshot received for:"+nodeRef+" snap:"+snap)
              nodeSnap=snap
              nodesSnapshot.add("Node"+snap.get("Node"),snap)
              println(nodesSnapshot)
              val path2 = "/output/%s.json".format("/Snapshot/Nodes")
              FileUtils.write(new File(path2), gson.toJson(nodesSnapshot), "UTF-8")
              AdaptedResponse("Success")
            }
            case Failure(_) => {
              context.log.info("Failed to get snapshot form node")
              AdaptedResponse("Fail")
            }

          }

          println(nodesSnapshot)
        }
        //Thread.sleep(3000)
        println(nodesSnapshot)
        //    val path2 = "output/%s.json".format("/Snapshot/Nodes")
        //    FileUtils.write(new File(path2), gson.toJson(nodesSnapshot), "UTF-8")
          Behaviors.same
    }
    Behaviors.empty
  }



}


//object DummyNode {
////  var DummyNodeServiceKey = ServiceKey[NodeRequest]("")
////  trait NodeRequest
////  case class FindNode(node: ActorRef[Nothing]) extends NodeRequest
////  case class getKeyValue(key: String,actorRef: ActorRef[Response]) extends NodeRequest
////  case class writeKeyValue(key: String, value: String) extends  NodeRequest
////  case class Response(message:String) extends NodeRequest
//
//  def apply(hash:BigInt): Behavior[NodeCommand] = Behaviors.setup { context =>
////    DummyNodeServiceKey=ServiceKey[NodeRequest](hash.toString())
////    context.system.receptionist ! Receptionist.Register(DummyNodeServiceKey, context.self)
//
//    Behaviors.receiveMessage {
//      case getKeyValue(key) =>
//        context.log.info("{} received read request by NODE ACTOR for key: {}", context.self.path.name, key)
//        //replyTo ! Response("Dummy value!")
//        Behaviors.same
//      case writeKeyValue(key,value) =>
//        context.log.info("{} received write request by NODE ACTOR for key: {}, value: {}", context.self.path.name, key, value)
//        Behaviors.same
//    }
//  }
//}
