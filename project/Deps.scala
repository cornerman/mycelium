import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Deps {
  import Def.{setting => dep}

  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.0.3")
  val scalajs = new {
    val dom = dep("org.scala-js" %%% "scalajs-dom" % "0.9.3")
  }
  val akka = new {
    private val version = "2.4.20"
    val http = dep("com.typesafe.akka" %% "akka-http" % "10.0.10")
    val actor = dep("com.typesafe.akka" %% "akka-actor" % version)
    val testkit = dep("com.typesafe.akka" %% "akka-testkit" % version)
  }
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.2.6")
}
