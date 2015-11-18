/**
 * KDTreeSpec.scala
 * Unit tests for KDTree.
 * Author: Nathan Flick
 */

package com.github.nflick.models.test

import com.github.nflick.models._

import scala.util.Random

import org.scalatest._
import breeze.linalg._

class KDTreeSpec extends FlatSpec with Matchers with VectorDist {

  "KDTree" should "return None for an empty sequence" in {
    val tree = KDTree(List())
    tree should equal (None)
  }

  it should "build a large tree" in {
    val rand = new Random(42)  
    val points = for ( i <- 1 to 10000 ) yield {
      DenseVector(rand.nextDouble(), rand.nextDouble(), rand.nextDouble)
    }

    val tree = KDTree[Int](points.map((_, 0))).getOrElse(null)

    def bruteNearest(to: Vector[Double], in: Seq[Vector[Double]]) = {
      in.minBy(squareDist(_, to))
    }

    (1 to 1000).
      map(i => DenseVector(rand.nextDouble(), rand.nextDouble(), rand.nextDouble())).
      foreach({ pt =>
        tree.nearest(pt) should equal (KDTree.Nearest(bruteNearest(pt, points), 0, pt))
      })
  }

  it should "perform well" in {
    val rand = new Random(42)  
    val points = for (i <- 1 to 7000) yield {
      DenseVector(rand.nextDouble(), rand.nextDouble(), rand.nextDouble)
    }

    val tree = KDTree[Int](points.map((_, 0))).getOrElse(null)

    def bruteNearest(to: Vector[Double], in: Seq[Vector[Double]]) = {
      in.minBy(squareDist(_, to))
    }

    (1 to 300000).
      map(i => DenseVector(rand.nextDouble(), rand.nextDouble(), rand.nextDouble())).
      foreach({ pt =>
        tree.nearest(pt)
      })
  }

}
