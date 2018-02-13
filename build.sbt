inThisBuild(Seq(
  organization := "com.github.cornerman",
  version      := "0.1.0-SNAPSHOT",

  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.11", "2.12.4"),

  resolvers ++=
    ("jitpack" at "https://jitpack.io") ::
    Nil
))

lazy val commonSettings = Seq(
  scalacOptions ++=
    "-encoding" :: "UTF-8" ::
    "-unchecked" ::
    "-deprecation" ::
    "-explaintypes" ::
    "-feature" ::
    "-language:_" ::
    "-Xfuture" ::
    "-Xlint" ::
    "-Ypartial-unification" ::
    "-Yno-adapted-args" ::
    "-Ywarn-infer-any" ::
    "-Ywarn-value-discard" ::
    "-Ywarn-nullary-override" ::
    "-Ywarn-nullary-unit" ::
    Nil,

  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) =>
        "-Ywarn-extra-implicit" ::
        Nil
      case _ =>
        Nil
    }
  }
)

lazy val root = (project in file("."))
  .aggregate(myceliumJS, myceliumJVM)
  .settings(commonSettings)
  .settings(
    publish := {},
    publishLocal := {},
  )

lazy val mycelium = crossProject
  .settings(commonSettings)
  .settings(
    name := "mycelium",
    libraryDependencies ++=
      Deps.scribe.value ::
      Deps.chameleon.value ::

      Deps.boopickle.value % Test ::
      Deps.scalaTest.value % Test ::
      Nil
  )
  .jvmSettings(
    libraryDependencies ++=
      Deps.akka.http.value ::
      Deps.akka.actor.value ::
      Deps.akka.stream.value ::
      Deps.okhttp.value ::
      Deps.akka.testkit.value % Test ::
      Nil
  )
  .jsSettings(
    scalacOptions ++=
      "-P:scalajs:sjsDefinedByDefault" ::
      Nil,
    npmDependencies in Compile ++=
      "reconnecting-websocket" -> "3.1.0" ::
      Nil,
    npmDependencies in Test ++=
      "html5-websocket" -> "2.0.1" ::
      Nil,
    libraryDependencies ++=
      Deps.scalajs.dom.value ::
      Nil
  )

lazy val myceliumJVM = mycelium.jvm
lazy val myceliumJS = mycelium.js
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
