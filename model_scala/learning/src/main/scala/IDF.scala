/**
 * IDF.scala
 * Inverse document frequency calculations.
 * Author: Nathan Flick
 */

package com.github.nflick.learning

import com.github.nflick.models._

import org.apache.spark.rdd._

object IDF {

  def fit(docs: RDD[Seq[String]], minOccurences: Int = 5): IDFModel = {
    val total = docs.count()
    val terms = docs.flatMap(identity).
      countByValue().
      filter(_._2 > minOccurences).
      toSeq.zipWithIndex.map({ case ((tag, count), index) =>
        tag -> (index -> math.log(total.toDouble / count.toDouble))
      }).toMap

    new IDFModel(terms)
  }

}
