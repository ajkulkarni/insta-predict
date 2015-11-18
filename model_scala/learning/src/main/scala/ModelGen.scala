/**
 * ModelGen.scala
 * Main file for project.
 * Author: Nathan Flick
 */

package com.github.nflick.learning

import com.github.nflick.models._

import scala.util.parsing.combinator.RegexParsers
import scala.language.postfixOps
import scala.util.Random

import java.util.Date
import java.text.{ParsePosition, SimpleDateFormat}
import java.io.File

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd._
import org.apache.spark.storage.StorageLevel
import breeze.linalg.{Vector, DenseVector}
import scopt.OptionParser

private[learning] case class Arguments(
  appName: String = "Geolocation Model Builder",
  command: Symbol = null,
  numPerClass: Int = 1000,
  loadExisting: Boolean = true,
  seed: Long = 42,
  sourcePath: String = null,
  modelPath: String = null,
  outputPath: String = null,
  folds: Int = 5
)

object ModelGen {

  import ModelExtensions._
  
  val argParser = new OptionParser[Arguments]("modelgen") {
    head("ModelGen", "0.0.1")
    
    opt[String]('a', "appname").
      valueName("<name>").
      action((x, c) => c.copy(appName = x)).
      text("Specify the Spark App Name.")

    note("\n")
    cmd("visualize").
      text("Generate KML file to visualize the location classes.").
      action((_, c) => c.copy(command = 'visualize)).
      children(
        arg[String]("<model>").
          action((x, c) => c.copy(modelPath = x)).
          text("The partition model."),

        arg[String]("<output>").
          action((x, c) => c.copy(outputPath = x)).
          text("Location to save the KML file.")
      )

    note("\n")
    cmd("train").
      text("Train the prediction model.").
      action((_, c) => c.copy(command = 'train)).
      children(
        opt[Int]('n', "num-per-class").
          valueName("<num>").
          action((x, c) => c.copy(numPerClass = x)).
          text("Number of samples per class (default 1000)."),

        opt[Unit]('i', "ignore-existing").
          action((_, c) => c.copy(loadExisting = false)).
          text("Do not used already generated components of model."),

        opt[Int]('s', "seed").
          valueName("<value>").
          action((x, c) => c.copy(seed = x)).
          text("Seed for random number generator."),

        arg[String]("<source>").
          action((x, c) => c.copy(sourcePath = x)).
          text("Source of samples in CSV format."),

        arg[String]("<output>").
          action((x, c) => c.copy(outputPath = x)).
          text("Location to save the generated model.")
      )

    note("\n")
    cmd("validate").
      text("Perform k-fold cross validation on the data set.").
      action((_, c) => c.copy(command = 'validate)).
      children(
        opt[Int]('n', "num-per-class").
          valueName("<num>").
          action((x, c) => c.copy(numPerClass = x)).
          text("Number of samples per class (default 1000)."),

        opt[Int]('s', "seed").
          valueName("<value>").
          action((x, c) => c.copy(seed = x)).
          text("Seed for random number generator."),

        opt[Int]('f', "folds").
          valueName("<folds>").
          action((x, c) => c.copy(folds = x)).
          text("Number of folds (default 5)."),

        arg[String]("<source>").
          action((x, c) => c.copy(sourcePath = x)).
          text("Source of samples in CSV format.")
      )

    checkConfig(c => if (c.command == null) failure("A command must be provided.") else success)
  }

  def main(args: Array[String]): Unit = {
    
    argParser.parse(args, Arguments()) match {
      case Some(arguments) => {
        val conf = new SparkConf().setAppName(arguments.appName)
        val sc = new SparkContext(conf)

        arguments.command match {
          case 'visualize => visualize(sc, arguments)
          case 'train => train(sc, arguments)
          case 'validate => crossValidate(sc, arguments)
          case _ =>
        }
      }

      case None =>
    }
  }

  def visualize(sc: SparkContext, args: Arguments): Unit = {
    val rand = new Random()
    def randomColor() = {
      val bytes = Array.ofDim[Byte](3)
      rand.nextBytes(bytes)
      f"80${bytes(0)}%02x${bytes(1)}%02x${bytes(2)}%02x"
    }

    val model = KMeansModel.load(args.modelPath + ".kmeans")
    val coords = Array.tabulate[String](model.numClasses)({ i =>
      val polygon = model.polygon(i)
      val c = for (p <- polygon) yield s"${p.lon},${p.lat},0.0\n"
      c.mkString("") + c(0)
    })

    val kml =
    <kml><Document>
      { for (i <- 0 until model.numClasses) yield
        <Placemark>
        <Style>
        <LineStyle><width>2</width></LineStyle>
        </Style>
        <Polygon>
        <outerBoundaryIs><LinearRing>
        <coordinates>
          { coords(i) }
        </coordinates>
        </LinearRing></outerBoundaryIs>
        </Polygon>
        </Placemark>
    } </Document></kml>
    
    scala.xml.XML.save(args.outputPath, kml)
  }

  def train(sc: SparkContext, args: Arguments): Unit = {
    val samples = loadSamples(sc, args.sourcePath)
    samples.persist(StorageLevel.MEMORY_AND_DISK_SER)

    val numClasses = (samples.count / args.numPerClass).toInt

    val clusters = if (args.loadExisting && new File(args.outputPath + ".kmeans").isFile) {
      KMeansModel.load(args.outputPath + ".kmeans")
    } else {
      val points = samples.map({ m =>
        val ecef = LLA(m.latitude, m.longitude, 0.0).toECEF
        DenseVector(ecef.x, ecef.y, ecef.z) : Vector[Double]
      })
      
      val kmeans = new KMeans(seed = args.seed)
      val clusters = kmeans.train(points, numClasses)
      clusters.save(args.outputPath + ".kmeans")
      clusters
    }

    val idf = if (args.loadExisting && new File(args.outputPath + ".idf").isFile) {
      IDFModel.load(args.outputPath + ".idf")
    } else {
      val idf = IDF.fit(samples.map(_.tags))
      idf.save(args.outputPath + ".idf")
      idf
    }

    val classifier = if (args.loadExisting && new File(args.outputPath + ".nb").isFile) {
      NaiveBayesModel.load(args.outputPath + ".nb")
    } else {
      val training = clusters.predict(samples).
        zip(idf.transform(samples.map(_.tags : Seq[String])))

      val nb = new NaiveBayes()
      val classifier = nb.train(training)
      classifier.save(args.outputPath + ".nb")
      classifier
    }

    samples.unpersist(false)
  }

  def crossValidate(sc: SparkContext, args: Arguments): Array[Double] = {
    val samples = loadSamples(sc, args.sourcePath)

    val seed = args.seed
    val k = args.folds
    val folds = samples.mapPartitionsWithIndex { case(index, data) =>
      val rand = new Random(index << 16 ^ seed)
      for (m <- data) yield (rand.nextInt(k), m)
    }

    folds.persist(StorageLevel.MEMORY_AND_DISK_SER)
    val count = folds.count
    val numClasses = (count * (k - 1) / (k * args.numPerClass)).toInt

    val levels = Array(1, 4, 16)
    val accuracy = Array.fill(levels.length)(0.0)

    for (f <- 0 until k) {
      val training = folds.filter(_._1 != f).map(_._2)
      val testing = folds.filter(_._1 == f).map(_._2)
      training.persist(StorageLevel.MEMORY_AND_DISK_SER)

      val points = samples.map({ m =>
        val ecef = LLA(m.latitude, m.longitude, 0.0).toECEF
        DenseVector(ecef.x, ecef.y, ecef.z) : Vector[Double]
      })
      val clusters = new KMeans(seed = seed).train(points, numClasses)
      val idf = IDF.fit(samples.map(_.tags))
      val classified = clusters.predict(samples).
        zip(idf.transform(samples.map(_.tags: Seq[String])))
      val classifier = new NaiveBayes().train(classified)
      val model = new PredictionModel(clusters, idf, classifier)

      val acc = model.validate(testing, levels)
      for (i <- 0 until levels.length) {
        println(s"Fold $f: Top ${levels(i)}: Accuracy = ${acc(i)}")
        accuracy(i) += acc(i)
      }

      training.unpersist(false)
    }

    folds.unpersist(false)
    for (i <- 0 until levels.length) {
      accuracy(i) /= k
      println(s"Overall: Top ${levels(i)}: Accuracy = ${accuracy(i)}")
    }
    accuracy
  }

  def loadSamples(sc: SparkContext, path: String): RDD[Media] = {
    sc.textFile(path).
      repartition(20).
      mapPartitions({ rows =>
        val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val pos = new ParsePosition(0)
        for {
          row <- rows.map(r => CSVLine.parse(r.trim))
          if row.length == 8
        } yield {
          pos.setIndex(0)
          Media(row(0).toLong, row(1).toLong, format.parse(row(2), pos), TagList.parse(row(3)),
            row(4).toLong, row(5), row(6).toDouble, row(7).toDouble)
        }
      })
  }
}

object CSVLine extends RegexParsers {
  override def skipWhitespace = false
  def COMMA = ","
  def QUOTE = "\""
  def TXT = "[^\",]".r

  def nonescaped: Parser[String] = (TXT*) ^^ { case ls => ls.mkString("") }
  def escaped: Parser[String] = (QUOTE~>((TXT|COMMA)*)<~QUOTE) ^^ { case ls => ls.mkString("") }
  def field: Parser[String] = escaped | nonescaped
  def record: Parser[List[String]] = (field~((COMMA~>field)*)) ^^ { case f~fs => f::fs }

  def parse(s: String) = parseAll(record, s) match {
    case Success(res, _) => res
    case _ => List[String]()
  }
}

object TagList extends RegexParsers {
  def LBRACE = "{"
  def RBRACE = "}"
  def COMMA = ","
  def TXT = "[^{},]".r

  def tag: Parser[String] = (TXT*) ^^ { case ls => ls.mkString("") }
  def record: Parser[List[String]] = (LBRACE~>(tag~((COMMA~>tag)*))<~RBRACE) ^^ { case f~fs => f::fs }

  def parse(s: String) = parseAll(record, s) match {
    case Success(res, _) => res
    case _ => List[String]()
  }
}
