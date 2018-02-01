import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Deps {
  import Def.{setting => dep}

  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.0.4")
  val scalajs = new {
    val dom = dep("org.scala-js" %%% "scalajs-dom" % "0.9.4")
  }
  val akka = new {
    private val version = "2.5.8"
    val http = dep("com.typesafe.akka" %% "akka-http" % "10.1.0-RC1")
    val stream = dep("com.typesafe.akka" %% "akka-stream" % version)
    val actor = dep("com.typesafe.akka" %% "akka-actor" % version)
    val testkit = dep("com.typesafe.akka" %% "akka-testkit" % version)
  }
  val chameleon = dep("com.github.cornerman" % "chameleon" % "54583b2")
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.2.6")
  val scribe = dep("com.outr" %%% "scribe" % "1.4.5")
}
