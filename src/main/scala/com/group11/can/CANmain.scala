package com.group11.can

import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.util.Timeout
import com.group11.can.CanMessageTypes._
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import scala.language.postfixOps

object CANmain {
  def main(args: Array[String]): Unit = {
    val conf: Config = ConfigFactory.load("application.conf")
    val netConf = conf.getConfig("CANnetworkConstants")

    val numNodes = netConf.getInt(("numNodes"))
    val canSystem = ActorSystem(netConf.getString("CANSystemName"))

    val canShardRegion: ActorRef = ClusterSharding(canSystem).start(
      typeName = "CanNodeRegion",
      entityProps = Props[CanNode](),
      settings = ClusterShardingSettings(canSystem),
      extractEntityId = CanNode.extractEntityId,
      extractShardId = CanNode.extractShardId
    )
    var id = BigInt(0)
    var peer = id

    val bootstrap = new ListBuffer[BigInt]()


    implicit val timeout = Timeout(10 seconds)
    val future= canShardRegion ? EntityEnvelope(id,JoinCan(canShardRegion,peer))
    val joinStatus = Await.result(future,timeout.duration).asInstanceOf[JoinDone]
    bootstrap.addOne(id)

    Thread.sleep(10)

    while (id < numNodes-1) {
      id += 1
      peer = bootstrap(scala.util.Random.nextInt(bootstrap.size))
      implicit val timeout = Timeout(20 seconds)
      val future = canShardRegion ? EntityEnvelope(id,JoinCan(canShardRegion,peer))
      val joinStatus = Await.result(future,timeout.duration).asInstanceOf[JoinDone]
      bootstrap.addOne(id)

      Thread.sleep(100)
    }

    Thread.sleep(2000)
    for(node <- bootstrap)
    {
      canShardRegion ? EntityEnvelope(node,PrintNeighbors)
      Thread.sleep(10)
    }

    val data = new ListBuffer[((Double,Double),Int)]()
    val xMax = netConf.getDouble("xMax")
    val yMax = netConf.getDouble("yMax")
    val totalRecords = netConf.getInt("totalRecords")
    while (data.size < totalRecords) {
      data.addOne((scala.util.Random.nextDouble()*xMax, scala.util.Random.nextDouble()*yMax), scala.util.Random.nextInt(1000))
    }

    // Write Initial data to CAN
    val recordsToWrite = netConf.getInt("recordsToWrite")
    var indexWrittenToCan = -1
    for (i <- 0 until recordsToWrite) {
      // Select random node to send write request
      val node = bootstrap(scala.util.Random.nextInt(bootstrap.size))
      canShardRegion ! EntityEnvelope(node , WriteData(data(i)._1,data(i)._2))
      indexWrittenToCan += 1
    }

    Thread.sleep(100)
//    log.info("Initial data written to CAN.. Proceed to requests..")
//    val readRequests = netConf.getInt("readRequests")
    val totalRequests = netConf.getInt("totalRequests")

    for (j <- 0 until totalRequests) {
      val requestType = scala.util.Random.nextInt(2)
      val node = bootstrap(scala.util.Random.nextInt(bootstrap.size))
      if (requestType == 0) {
        val recordToRead = scala.util.Random.nextInt(indexWrittenToCan+1)
        canShardRegion ! EntityEnvelope(node , ReadData(data(recordToRead)._1))
      }
      else if (indexWrittenToCan < totalRecords-1){
        canShardRegion ! EntityEnvelope(node , WriteData(data(indexWrittenToCan+1)._1,data(indexWrittenToCan+1)._2))
        indexWrittenToCan +=1
        Thread.sleep(100)
      }
    }

  }

}
