/**
 * KMeans.scala
 * Optimized KMeans using KDTrees.
 * Author: Nathan Flick
 */

package com.github.nflick.learning

import com.github.nflick.models._

import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import java.io._

import org.apache.spark.SparkContext
import org.apache.spark.rdd._
import org.apache.spark.storage.StorageLevel
import breeze.linalg._

class KMeans(maxIterations: Int = 50, initSteps: Int = 5,
  seed: Long = 42, epsilon: Double = 1e-4) extends VectorDist {

  def train(points: RDD[Vector[Double]], numClusters: Int): KMeansModel = {
    // Adapted from https://github.com/apache/spark/blob/master/mllib/src/main/scala/org/apache/spark/mllib/clustering/KMeans.scala
    // Optimized for spatial data through the use of KD-Trees for faster lookups
    // of the nearest center.

    points.persist(StorageLevel.MEMORY_ONLY)
    val rand = new Random(seed)
    var centers = initParallel(points, numClusters)
    var iteration = 0
    var changed = true

    while (iteration < maxIterations && changed) {
      val kdtree = KDTree(centers.zipWithIndex).get
      val bcTree = points.context.broadcast(kdtree)
      val bcCenters = points.context.broadcast(centers)
      val costAccum = points.context.accumulator(0.0)

      val totalContribs = points.mapPartitions { points =>
        val centers = bcCenters.value
        val tree = bcTree.value
        val dims = centers(0).size
        val sums = Array.fill(centers.length)(DenseVector.zeros[Double](dims))
        val counts = Array.fill(centers.length)(0L)

        points.foreach { point =>
          val nearest = tree.nearest(point)
          costAccum += nearest.sqdist
          sums(nearest.tag) :+= nearest.value
          counts(nearest.tag) += 1
        }

        val contribs = for (k <- 0 until centers.length) yield {
          (k, (sums(k), counts(k)))
        }
        contribs.iterator

      }.reduceByKey { (x, y) =>
        (x._1 :+= y._1, x._2 + y._2)
      }.collectAsMap()

      changed = false
      for (k <- 0 until centers.length) {
        val (sum, count) = totalContribs(k)
        if (count != 0) {
          val newCenter = sum * (1.0 / count.toDouble)
          if (squareDist(newCenter, centers(k)) > epsilon * epsilon) { changed = true }
          centers(k) = newCenter
        }
      }

      iteration += 1
    }

    points.unpersist(false)
    new KMeansModel(centers)
  }

  private def initParallel(points: RDD[Vector[Double]], numClusters: Int):
      Array[Vector[Double]] = {
    // Adapted from https://github.com/apache/spark/blob/master/mllib/src/main/scala/org/apache/spark/mllib/clustering/KMeans.scala.

    val centers = ArrayBuffer.empty[Vector[Double]]
    var costs = points.map(_ => Double.PositiveInfinity)

    // Initialize the first center to a random point.
    val rand = new Random(seed)
    val sample = points.takeSample(true, 1, rand.nextLong()).toSeq.head.toDenseVector
    val newCenters = ArrayBuffer[Vector[Double]](sample)

    var step = 0
    while (step < initSteps) {
      val kdtree = KDTree(newCenters.zipWithIndex).get
      val bcTree = points.context.broadcast(kdtree)
      val preCosts = costs

      costs = points.zip(preCosts).map { case (point, cost) =>
        val nearest = bcTree.value.nearest(point)
        math.min(nearest.sqdist, cost)
      }.persist(StorageLevel.MEMORY_AND_DISK)

      val sumCosts = costs.aggregate(0.0)((u, v) => u + v, (u, v) => u + v)
      preCosts.unpersist(blocking = false)

      val seed = rand.nextInt()
      val chosen = points.zip(costs).mapPartitionsWithIndex { (index, pointsWithCosts) =>
        val r = new Random(seed ^ (step << 16) ^ index)
        pointsWithCosts.flatMap { case (p, c) =>
          if (r.nextDouble() < 2.0 * c * numClusters / sumCosts) Some(p) else None
        }
      }.collect()

      centers ++= newCenters
      newCenters.clear()

      chosen.foreach { case (p) =>
        newCenters += p.toDenseVector
      }

      step += 1
    }

    println("KMeans|| centers: " + centers.size.toString)
    centers ++= newCenters
    newCenters.clear()
    costs.unpersist(false)

    val kdtree = KDTree(centers.zipWithIndex).get
    val bcTree = points.context.broadcast(kdtree)

    val weightMap = points.map(p => (bcTree.value.nearest(p).tag, 1.0)).reduceByKey(_ + _).
      collectAsMap()

    val weights = Array.tabulate[Double](centers.length)(weightMap.getOrElse(_, 0.0))
    val finalCenters = localKMeansPlusPlus(centers.toArray, weights, numClusters)

    assert(finalCenters.length == numClusters,
      s"Got ${finalCenters.length} centers from InitParallel, expected ${numClusters}.")
    finalCenters
  }

  private def localKMeansPlusPlus(points: Array[Vector[Double]], weights: Array[Double],
      numClusters: Int): Array[Vector[Double]] = {
    // Adapted from https://github.com/apache/spark/blob/master/mllib/src/main/scala/org/apache/spark/mllib/clustering/LocalKMeans.scala.

    val rand = new Random(seed)
    val dimensions = points(0).size
    val centers = new Array[Vector[Double]](numClusters)

    // Initialize centers with kmeans++ initialization procedure.
    centers(0) = pickWeighted(rand, points, weights)
    for (i <- 1 until numClusters) {
      val kdtree = KDTree(centers.view.take(i).toList.zipWithIndex).get
      val sum = points.view.zip(weights).map { case (p, w) =>
        w * kdtree.nearest(p).sqdist
      }.sum

      val r = rand.nextDouble * sum
      var cumulativeScore = 0.0
      var j = 0
      while (j < points.length && cumulativeScore < r) {
        cumulativeScore += weights(j) * kdtree.nearest(points(j)).sqdist
        j += 1
      }

      if (j == 0) {
        centers(i) = points(0).toDenseVector
      } else {
        centers(i) = points(j - 1).toDenseVector
      }
    }

    val oldClosest = Array.fill(points.length)(-1)
    var iteration = 0
    var moved = true
    while (moved && iteration < maxIterations) {
      moved = false
      val counts = Array.fill(numClusters)(0.0)
      val sums = Array.fill(numClusters)(DenseVector.zeros[Double](dimensions))

      val kdtree = KDTree(centers.zipWithIndex).get
      var i = 0
      while (i < points.length) {
        val p = points(i)
        val index = kdtree.nearest(p).tag
        sums(index) :+= (p * weights(i))
        counts(index) += weights(i)

        if (index != oldClosest(i)) {
          moved = true
          oldClosest(i) = index
        }

        i += 1
      }

      // Update centers.
      var j = 0
      while (j < numClusters) {
        if (counts(j) == 0.0) {
          centers(j) = points(rand.nextInt(points.length))
        } else {
          sums(j) :*= (1.0 / counts(j))
          centers(j) = sums(j)
        }
        j += 1
      }
      iteration += 1
    }

    centers
  }

  private def pickWeighted(rand: Random, points: Array[Vector[Double]],
      weights: Array[Double]): Vector[Double] = {
    // Adapted from https://github.com/apache/spark/blob/master/mllib/src/main/scala/org/apache/spark/mllib/clustering/LocalKMeans.scala

    val r = rand.nextDouble() * weights.sum
    var i = 0
    var curWeight = 0.0
    while (i < points.length && curWeight < r) {
      curWeight += weights(i)
      i += 1
    }

    if (i == 0) points(0) else points(i - 1)
  }

}
