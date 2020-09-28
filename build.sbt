publishArtifact := false

val componentVersion = "0.5.0"
organization := "org.dmonix"
version := componentVersion


val baseSettings = Seq(
  organization := "org.dmonix",
  version := componentVersion,
  scalaVersion := "2.12.11",
  crossScalaVersions := Seq("2.11.12", "2.12.12", "2.13.3"),
  scalacOptions := Seq("-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-unchecked",
    "-deprecation",
    "-encoding", "utf8"),
  libraryDependencies ++= Seq(
    `spray-json`,
    `slf4j-api`,
    `specs2-core` % "test",
    `specs2-mock` % "test",
    `specs2-junit` % "test",
    `specs2-matcher-extra` % "test",
    `akka-http`% "test",
    `akka-actor` % "test",
    `akka-stream` % "test",
    `logback-classic` % "test"
  )
)

lazy val common = (project in file("consul-common"))
  .settings(baseSettings)
  .settings(
    name := "consul-common"
  )

lazy val sim = (project in file("consul-recipes"))
  .settings(baseSettings)
  .settings(
    name := "consul-recipes"
  )
  .dependsOn(common, recipes)

lazy val recipes = (project in file("consul-sim"))
  .settings(baseSettings)
  .settings(
    name := "consul-sim",
    libraryDependencies ++= Seq(
      `akka-http`,
      `akka-actor`,
      `akka-stream`,
      `akka-stream-testkit` % "test",
      `akka-http-testkit` % "test"
    )
  )
  .dependsOn(common)
