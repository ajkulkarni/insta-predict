/**
 * ModelExtensions.scala
 * Implicit methods handling Spark RDD's in model classes,
 * allowing dependency on Spark to be factored out of models.
 * Author: Nathan Flick
 */

package com.github.nflick.learning

import com.github.nflick.models._

import org.apache.spark.rdd.RDD
import breeze.linalg.SparseVector

object ModelExtensions {

  implicit class IDFModelExtensions(val self: IDFModel) extends AnyVal {

    def transform(documents: RDD[Seq[String]]): RDD[SparseVector[Double]] = {
      val bcModel = documents.context.broadcast(self)
      documents.map(bcModel.value.transform(_))
    }

  }

  implicit class KMeansModelExtensions(val self: KMeansModel) extends AnyVal {

    def predict(media: RDD[Media]): RDD[Int] = {
      val bcModel = media.context.broadcast(self)
      media.map(bcModel.value.predict(_))
    }

  }

  implicit class PredictionModelExtensions(val self: PredictionModel) extends AnyVal {

    def validate(media: RDD[Media], levels: Array[Int]): Array[Double] = {
      val count = media.count
      val bcModel = media.context.broadcast(self)
      media.map(bcModel.value.validate(_, levels)).
        fold(Array.fill(levels.length)(0))({ (a, b) =>
          for (i <- 0 until a.length) a(i) += b(i)
          a
        }).map(_.toDouble / count)
    }

  }

}