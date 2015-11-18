/**
 * Boot.scala
 * Startup file for the prediction service.
 * Author: Nathan Flick
 */

package com.github.nflick.service

import com.github.nflick.models._

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scopt.OptionParser

private[service] case class Arguments(
  modelPath: String = null,
  interface: String = "localhost",
  port: Int = 8080
)

object Boot {

  val argParser = new OptionParser[Arguments]("service") {
    head("Prediction Service", "0.0.1")
    
    arg[String]("model").
      action((x, c) => c.copy(modelPath = x)).
      text("The tag prediction model.")

    opt[String]('i', "interface").
      valueName("<interface>").
      action((x, c) => c.copy(interface = x)).
      text("Interface to bind to.")
    
    opt[Int]('p', "port").
      valueName("<port>").
      action((x, c) => c.copy(port = x)).
      text("Port to bind to.")
  }

  def main(args: Array[String]): Unit = {
    argParser.parse(args, Arguments()) match {
      case Some(arguments) => {
        // Adapted from https://github.com/spray/spray-template/blob/on_spray-can_1.3/src/main/scala/com/example/Boot.scala
        val model = PredictionModel.load(arguments.modelPath)

        implicit val system = ActorSystem("on-spray-can")
        implicit val timeout = Timeout(5.seconds)
        val service = system.actorOf(Props(classOf[PredictionServiceActor], model), "prediction-service")
        IO(Http) ? Http.Bind(service, interface = arguments.interface, port = arguments.port)
      }

      case None =>
    }
  }

}