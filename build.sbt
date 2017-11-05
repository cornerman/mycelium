inThisBuild(Seq(
  organization := "com.github.cornerman",
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.11", "2.12.4"),
  version      := "0.1.0-SNAPSHOT",
  scalacOptions ++=
    "-encoding" :: "UTF-8" ::
    "-unchecked" ::
    "-deprecation" ::
    "-explaintypes" ::
    "-feature" ::
    "-language:_" ::
    "-Xcheckinit" ::
    "-Xfuture" ::
    "-Xlint" ::
    "-Yno-adapted-args" ::
    "-Ywarn-dead-code" ::
    "-Ywarn-unused" ::
    "-Ywarn-infer-any" ::
    "-Ywarn-nullary-override" ::
    "-Ywarn-nullary-unit" ::
    Nil,
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) =>
        "-Ywarn-extra-implicit" ::
        Nil
      case _             =>
        Nil
    }
  }
))

resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")
enablePlugins(ScalaJSPlugin)

lazy val root = (project in file(".")).
  aggregate(myceliumJS, myceliumJVM)

lazy val mycelium = crossProject.
  settings(
    name := "mycelium",
    libraryDependencies ++=
      Deps.scribe.value ::
      Deps.boopickle.value % Test ::
      Deps.scalaTest.value % Test ::
      Nil
  )
  .jvmSettings(
    libraryDependencies ++= (
      Deps.akka.http.value ::
      Deps.akka.actor.value ::
      Deps.akka.testkit.value % Test ::
      Nil
    )
  )
  .jsSettings(
    libraryDependencies ++= (
      Deps.scalajs.dom.value ::
      Nil
    )
  )

lazy val myceliumJS = mycelium.js
lazy val myceliumJVM = mycelium.jvm
