import sbt._
import Keys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Deps {
  import Def.{setting => dep}

  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.2.13")
  val scalajs = new {
    val dom = dep("org.scala-js" %%% "scalajs-dom" % "2.2.0")
  }
  val akka = new {
    private val version = "2.6.16"
    val http            = dep("com.typesafe.akka" %% "akka-http" % "10.2.9")
    val stream          = dep("com.typesafe.akka" %% "akka-stream" % version)
    val actor           = dep("com.typesafe.akka" %% "akka-actor" % version)
    val testkit         = dep("com.typesafe.akka" %% "akka-testkit" % version)
  }
  val chameleon = dep("com.github.cornerman" %%% "chameleon" % "0.3.5")
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.4.0")
  val scribe    = dep("com.outr" %%% "scribe" % "3.10.3")
}
