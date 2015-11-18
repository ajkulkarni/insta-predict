/**
 * Voronoi.scala
 * Computes the voronoi diagram for cluster centers
 * using the convex hull.
 * Author: Nathan Flick
 */

package com.github.nflick.models

import scala.collection.mutable.ArrayBuffer
import scala.collection._

import breeze.linalg._
import quickhull3d._

object Voronoi {

  def compute(vectors: Array[Vector[Double]]): Array[Array[LLA]] = {
    // Normalize to surface of earth
    val points = vectors map { v =>
      val lla = ECEF(v(0), v(1), v(2)).toLLA
      val normal = LLA(lla.lat, lla.lon, 0.0).toECEF
      new Point3d(normal.x, normal.y, normal.z)
    }

    val hull = new QuickHull3D(points.toArray)
    val vertices = hull.getVertices()
    val polygons = new Array[ArrayBuffer[DenseVector[Double]]](vertices.length)
    for (i <- 0 until vertices.length) {
      polygons(i) = ArrayBuffer[DenseVector[Double]]()
    }

    for (face <- hull.getFaces()) {
      val center = centroid(face.map(i => toVector(vertices(i))))
      for (vertex <- face) {
        polygons(vertex) += center
      }
    }

    val ordered = new Array[Array[LLA]](vectors.length)
    val mapping = hull.getVertexPointIndices()
    for (i <- 0 until ordered.length) {
      ordered(mapping(i)) = orderCCW(polygons(i)).map(toLLA(_)).toArray
    }

    ordered
  }

  private def centroid(points: Seq[DenseVector[Double]]): DenseVector[Double] = {
    val center = DenseVector.zeros[Double](points.head.size)
    for (p <- points) {
      center :+= p
    }
    center :*= 1.0 / points.length
    center
  }

  private def orderCCW(points: IndexedSeq[DenseVector[Double]]): Seq[DenseVector[Double]] = {
    val center = centroid(points)
    val normal = cross(points(0) - center, points(1) - center)
    if ((center dot normal) < 0.0) {
      normal :*= -1.0
    }

    val first = points.head
    points.sortBy({ v =>
      val a = first - center
      val b = v - center
      val angle = math.acos((a dot b) / (norm(a) * norm(b)))
      if ((normal dot cross(a, b)) > 0.0) angle
      else 2.0 * math.Pi - angle
    })
  }

  private def toLLA(v: Vector[Double]): LLA = {
    val lla = ECEF(v(0), v(1), v(2)).toLLA
    LLA(lla.lat, lla.lon, 0.0) // Normalize to sea level
  }

  private def toVector(p: Point3d): DenseVector[Double] = {
    DenseVector(p.get(0), p.get(1), p.get(2))
  }

}