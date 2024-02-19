import sbt._
import Keys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Deps {
  import Def.{setting => dep}

  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.2.17")
  val scalajs = new {
    val dom = dep("org.scala-js" %%% "scalajs-dom" % "2.8.0")
  }
  val pekko = new {
    private val version = "1.0.1"
    val http            = dep("org.apache.pekko" %% "pekko-http" % "1.0.1")
    val stream          = dep("org.apache.pekko" %% "pekko-stream" % version)
    val actor           = dep("org.apache.pekko" %% "pekko-actor" % version)
    val testkit         = dep("org.apache.pekko" %% "pekko-testkit" % version)
  }
  val chameleon = dep("com.github.cornerman" %%% "chameleon" % "0.3.7")
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.4.0")
  val scribe    = dep("com.outr" %%% "scribe" % "3.12.0")
}
