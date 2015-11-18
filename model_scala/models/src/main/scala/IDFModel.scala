/**
 * IDFModel.scala
 * Inverse document frequency calculations model.
 * Author: Nathan Flick
 */

package com.github.nflick.models

import java.io._

import breeze.linalg.{SparseVector, VectorBuilder}

@SerialVersionUID(2L)
class IDFModel(terms: Map[String, (Int, Double)]) extends Serializable {

  def transform(features: Seq[String]): SparseVector[Double] = {
    val builder = new VectorBuilder[Double](terms.size)
    features.foreach({ f =>
      terms.get(f) match {
        case Some((index, tfidf)) => builder.add(index, tfidf)
        case None =>
      }
    })
    builder.toSparseVector()
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

object IDFModel {

  def load(path: String): IDFModel = {
    val objStream = new ObjectInputStream(new FileInputStream(path))
    try {
      val model = objStream.readObject() match {
        case m: IDFModel => m
        case other => throw new ClassCastException(s"Expected IDFModel, got ${other.getClass}.")
      }
      model
    } finally {
      objStream.close()
    }
  }

}