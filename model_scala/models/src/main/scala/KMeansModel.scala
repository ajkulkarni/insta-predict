/**
 * KMeansModel.scala
 * Optimized KMeans model using KDTrees.
 * Author: Nathan Flick
 */

package com.github.nflick.models

import java.io._

import breeze.linalg.{Vector, DenseVector}

trait VectorDist {

  def squareDist(v1: Vector[Double], v2: Vector[Double]): Double = {
    val s = v1.size
    require(s == v2.size, "Vector sizes must be equal")

    var i = 0
    var dist = 0.0
    while (i < s) {
      val score = v1(i) - v2(i)
      dist += score * score
      i += 1
    }

    dist
  }

}

object KDTree {

  def apply[T](points: Seq[(Vector[Double], T)], depth: Int = 0): Option[KDNode[T]] = {
    val dim = points.headOption match {
      case None => 0
      case Some((v, t)) => v.size
    }

    if (points.isEmpty || dim < 1) None
    else {
      val axis = depth % dim
      val sorted = points.sortBy(_._1(axis))
      val median = sorted(sorted.size / 2)._1(axis)
      val (left, right) = sorted.partition(_._1(axis) < median)
      Some(KDNode(right.head._1, right.head._2, apply(left, depth + 1), apply(right.tail, depth + 1), axis))
    }
  }

  @SerialVersionUID(2L)
  case class KDNode[T](value: Vector[Double], tag: T,
      left: Option[KDNode[T]], right: Option[KDNode[T]], axis: Int) extends Serializable {

    def nearest(to: Vector[Double]): Nearest[T] = {
      val default = Nearest[T](value, tag, to)
      val dist = to(axis) - value(axis)

      lazy val bestL = left.map(_.nearest(to)).getOrElse(default)
      lazy val bestR = right.map(_.nearest(to)).getOrElse(default)
      val branch1 = if (dist < 0) bestL else bestR
      val best = if (branch1.sqdist < default.sqdist) branch1 else default

      if (dist * dist < best.sqdist) {
        val branch2 = if (dist < 0) bestR else bestL
        if (branch2.sqdist < best.sqdist) branch2 else best
      } else best
    }

  }

  @SerialVersionUID(2L)
  case class Nearest[T](value: Vector[Double], tag: T, to: Vector[Double]) extends VectorDist {
    val sqdist = squareDist(value, to)
  }

}

@SerialVersionUID(2L)
class KMeansModel(centers: Array[Vector[Double]]) extends Serializable {

  private val kdtree = KDTree(centers.zipWithIndex).get

  @transient
  private var polygons = Voronoi.compute(centers)

  def predict(m: Media): Int = {
    val ecef = LLA(m.latitude, m.longitude, 0.0).toECEF
    val v = DenseVector(ecef.x, ecef.y, ecef.z)
    predict(v)
  }

  def predict(v: Vector[Double]): Int = kdtree.nearest(v).tag

  def numClasses: Int = centers.length

  def center(label: Int): LLA = {
    val v = centers(label)
    ECEF(v(0), v(1), v(2)).toLLA
  }

  def polygon(label: Int): Array[LLA] = polygons(label)

  def save(path: String): Unit = {
    val objStream = new ObjectOutputStream(new FileOutputStream(path))
    try {
      objStream.writeObject(this)
    } finally {
      objStream.close()
    }
  }

  @throws(classOf[java.io.IOException])
  private def readObject(in: ObjectInputStream): Unit = {
    in.defaultReadObject()
    polygons = Voronoi.compute(centers)
  }

}

object KMeansModel {

  def load(path: String): KMeansModel = {
    val objStream = new ObjectInputStream(new FileInputStream(path))
    try {
      objStream.readObject() match {
        case m: KMeansModel => m
        case other => throw new ClassCastException(s"Expected KMeansModel, got ${other.getClass}.")
      }
    } finally {
      objStream.close()
    }
  }

}
