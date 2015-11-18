lazy val commonSettings = Seq(
  organization := "com.github.nflick",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.10.4",
  scalacOptions += "-feature",

  libraryDependencies ++= Seq(
    "org.scalanlp"      %% "breeze"         % "0.10",
    "org.scalatest"     %% "scalatest"      % "2.0"   % "test"
  ),

  resolvers ++= Seq(
    Resolver.sonatypeRepo("public"),
    "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
  )
)

lazy val models = project.in(file("models")).
  settings(commonSettings: _*).
  settings(
    name := "Models"
  )

lazy val learning = project.in(file("learning")).
  dependsOn(models).
  settings(commonSettings: _*).
  settings(
    name := "Learning",
    libraryDependencies ++= Seq(
      "org.apache.spark"  %% "spark-core"     % "1.5.1" % "provided",
      "org.apache.spark"  %% "spark-mllib"    % "1.5.1" % "provided",
      "com.github.scopt"  %% "scopt"          % "3.3.0",
      "com.github.quickhull3d" % "quickhull3d" % "1.0.0"
    )
  )

lazy val service = project.in(file("service")).
  dependsOn(models).
  settings(commonSettings: _*).
  settings(
    name := "Service",
    libraryDependencies ++= Seq(
      "com.github.scopt"  %% "scopt"          % "3.3.0",
      "io.spray"          %% "spray-can"      % "1.3.3",
      "io.spray"          %% "spray-routing"  % "1.3.3",
      "io.spray"          %% "spray-json"     % "1.3.2",
      "com.typesafe.akka" %% "akka-actor"     % "2.3.9"
    )
  )

lazy val root = project.in(file(".")).
  aggregate(models, learning, service)
