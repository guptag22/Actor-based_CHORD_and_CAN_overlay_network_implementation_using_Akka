package com.group11.hw3

import akka.actor.typed.ActorSystem


/*
Class used to test the basic working of the akka http server.
 */
object main_test {
  def main(args: Array[String]): Unit = {

    val sys=ActorSystem(serverRedone(),"Chord-server-system")
    Thread.sleep(2000)
    sys ! CaptureGlobalSnapshot()
    //ActorSystem(UserSystem(),"Users")


  }

}
