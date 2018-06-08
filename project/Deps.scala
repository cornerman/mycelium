import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._

object Deps {
  import Def.{setting => dep}

  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.0.8")
  val scalajs = new {
    val dom = dep("org.scala-js" %%% "scalajs-dom" % "0.9.7")
  }
  val akka = new {
    private val version = "2.5.23"
    val http = dep("com.typesafe.akka" %% "akka-http" % "10.1.9")
    val stream = dep("com.typesafe.akka" %% "akka-stream" % version)
    val actor = dep("com.typesafe.akka" %% "akka-actor" % version)
    val testkit = dep("com.typesafe.akka" %% "akka-testkit" % version)
  }
  val chameleon = dep("com.github.cornerman.chameleon" %%% "chameleon" % "73ac23c")
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.3.1")
  val scribe = dep("com.outr" %%% "scribe" % "2.7.9")
  val monix = dep("io.monix" %%% "monix" % "3.0.0-RC3")
}
