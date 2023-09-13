inThisBuild(
  Seq(
    organization := "com.github.cornerman",
    scalaVersion := "2.12.17",
    crossScalaVersions := Seq("2.12.17", "2.13.11"),
    licenses := Seq("MIT License" -> url("https://opensource.org/licenses/MIT")),
    homepage := Some(url("https://github.com/cornerman/mycelium")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/cornerman/mycelium"),
        "scm:git:git@github.com:cornerman/mycelium.git",
        Some("scm:git:git@github.com:cornerman/mycelium.git"),
      ),
    ),
    pomExtra :=
      <developers>
      <developer>
        <id>jkaroff</id>
        <name>Johannes Karoff</name>
        <url>https://github.com/cornerman</url>
      </developer>
    </developers>,
  ),
)

lazy val commonSettings = Seq(
  scalacOptions --= Seq(
    "-Wunused:explicits",
    "-Ywarn-unused:params",
    "-Wunused:params",
    "-Xfatal-warnings",
  ),
  libraryDependencies ++=
    Deps.boopickle.value   % Test ::
      Deps.scalaTest.value % Test ::
      Nil,
)

lazy val jsSettings = Seq(
  scalacOptions += {
    val githubRepo    = "cornerman/mycelium"
    val local         = baseDirectory.value.toURI
    val subProjectDir = baseDirectory.value.getName
    val remote        = s"https://raw.githubusercontent.com/${githubRepo}/${git.gitHeadCommit.value.get}"
    s"-P:scalajs:mapSourceURI:$local->$remote/${subProjectDir}/"
  },
)

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(commonSettings)
  .jsSettings(jsSettings)
  .settings(
    name := "mycelium-core",
    libraryDependencies ++=
      Deps.scribe.value ::
        Deps.chameleon.value ::
        Nil,
  )

lazy val clientJS = project
  .in(file("client-js"))
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(core.js)
  .settings(commonSettings)
  .settings(jsSettings)
  .settings(
    name := "mycelium-client-js",
    npmDependencies in Compile ++=
      "reconnecting-websocket" -> "4.1.10" ::
        Nil,
    npmDependencies in Test ++=
      "html5-websocket" -> "2.0.1" ::
        Nil,
    libraryDependencies ++=
      Deps.scalajs.dom.value ::
        Nil,
  )

lazy val akka = project
  .in(file("akka"))
  .dependsOn(core.jvm)
  .settings(commonSettings)
  .settings(
    name := "mycelium-akka",
    libraryDependencies ++=
      Deps.akka.http.value ::
        Deps.akka.actor.value ::
        Deps.akka.stream.value ::
        Deps.akka.testkit.value % Test ::
        Nil,
  )

lazy val root = project
  .in(file("."))
  .settings(
    name := "mycelium-root",
    skip in publish := true,
  )
  .aggregate(core.js, core.jvm, clientJS, akka)
