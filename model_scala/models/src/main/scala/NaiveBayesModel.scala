/**
 * KMeans.scala
 * Optimized Naive Bayes model using Sparse matrices.
 * Author: Nathan Flick
 */

package com.github.nflick.models

import java.io._

import breeze.linalg.SparseVector

@SerialVersionUID(2L)
class NaiveBayesModel(val labels: Array[Int], val pi: Array[Double],
    val theta: Array[SparseVector[Double]], val thetaLogDenom: Array[Double])
    extends Serializable {

  def predictAll(features: SparseVector[Double]): Seq[(Int, Double)] = {
    val logProb = multinomial(features)
    val probs = posteriorProbabilities(logProb)
    probs.zipWithIndex.sortBy(-_._1).map(t => (labels(t._2), t._1))
  }

  def predictMultiple(features: SparseVector[Double], count: Int): Seq[(Int, Double)] = {
    predictAll(features).take(count)
  }

  def predict(features: SparseVector[Double]): Int = {
    val logProb = multinomial(features)
    val index = logProb.indexOf(logProb.max)
    labels(index)
  }

  private def multinomial(features: SparseVector[Double]): Array[Double] = {
    Array.tabulate[Double](theta.length)(i =>
      product(features, theta(i), thetaLogDenom(i)) + pi(i))
  }

  private def product(features: SparseVector[Double], probs: SparseVector[Double],
      denom: Double): Double = {

    require(features.size == probs.size, "Vectors are different sizes")
    var product = 0.0
    var offset = 0
    while (offset < features.activeSize) {
      product += features.valueAt(offset) * (probs(features.indexAt(offset)) - denom)
      offset += 1
    }

    product
  }

  private def posteriorProbabilities(logProb: Array[Double]): Array[Double] = {
    val maxLog = logProb.max
    val scaledProbs = logProb.map(lp => math.exp(lp - maxLog))
    val probSum = scaledProbs.sum
    scaledProbs.map(_ / probSum)
  }

  def save(path: String): Unit = {
    val objStream = new ObjectOutputStream(new FileOutputStream(path))
    try {
      objStream.writeObject(this)
    } finally {
      objStream.close()
    }
  }
}

object NaiveBayesModel {

  def load(path: String): NaiveBayesModel = {
    val objStream = new ObjectInputStream(new FileInputStream(path))
    try {
      val model = objStream.readObject() match {
        case m: NaiveBayesModel => m
        case other => throw new ClassCastException(s"Expected NaiveBayesModel, got ${other.getClass}.")
      }
      model
    } finally {
      objStream.close()
    }
  }

}
