import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Deps {
  import Def.{setting => dep}

  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.0.4")
  val scalajs = new {
    val dom = dep("org.scala-js" %%% "scalajs-dom" % "0.9.5")
  }
  val akka = new {
    private val version = "2.5.11"
    val http = dep("com.typesafe.akka" %% "akka-http" % "10.1.0")
    val stream = dep("com.typesafe.akka" %% "akka-stream" % version)
    val actor = dep("com.typesafe.akka" %% "akka-actor" % version)
    val testkit = dep("com.typesafe.akka" %% "akka-testkit" % version)
  }
  val okhttp = dep("com.squareup.okhttp3" % "okhttp" % "3.9.1")
  val chameleon = dep("com.github.cornerman.chameleon" %%% "chameleon" % "7dacc9f")
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.3.0")
  val scribe = dep("com.outr" %%% "scribe" % "2.3.2")
}
