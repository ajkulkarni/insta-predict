/**
 * PredictionModel.scala
 * Serve predictions over an HTTP endpoint.
 * Adapted from https://github.com/spray/spray-template/blob/on_spray-can_1.3/src/main/scala/com/example/MyService.scala.
 * Author: Nathan Flick
 */

package com.github.nflick.service

import com.github.nflick.models._

import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._
import spray.json._
import spray.httpx.SprayJsonSupport
import scala.language.postfixOps

class PredictionServiceActor(val model: PredictionModel) extends Actor with PredictionService {
  def actorRefFactory = context
  def receive = runRoute(predict)
}

object PredictionJsonProtocol extends DefaultJsonProtocol {
  implicit val predictionFormat = jsonFormat3(Prediction)
}

trait PredictionService extends HttpService {

  val model: PredictionModel
  val splitter = "[+,]".r

  val predict = {
    import SprayJsonSupport._
    import PredictionJsonProtocol._
    path("predict" / Segment ~ PathEnd) { tags =>
      get  {
        parameters("count".as[Int] ?) { count =>
          val terms = splitter.split(tags).map(_.toLowerCase)
          val result = count match {
            case Some(c) => model.predictMultiple(terms, c)
            case None => model.predictHeuristic(terms)
          }
          complete(JsObject(
            "status" -> JsNumber(200),
            "error" -> JsString(""),
            "result" -> result.toJson)
          )
        }
      }
    }
  }

}