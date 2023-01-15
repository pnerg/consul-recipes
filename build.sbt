publishArtifact := false

val componentVersion = "1.1.0"
organization := "org.dmonix"
version := componentVersion

val baseSettings = Seq(
  organization := "org.dmonix",
  version := componentVersion,
  scalaVersion := "2.13.10",
  crossScalaVersions := Seq("2.11.12", "2.12.17", "2.13.10"),
  scalacOptions := Seq(
    "-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-unchecked",
    "-deprecation",
    "-encoding",
    "utf8"
  ),
  libraryDependencies ++= Seq(
    `spray-json`,
    `slf4j-api`,
    `specs2-core` % "test",
    `specs2-mock` % "test",
    `specs2-junit` % "test",
    `specs2-matcher-extra` % "test",
    `akka-http` % "test",
    `akka-actor` % "test",
    `akka-stream` % "test",
    `logback-classic` % "test"
  )
)

val integrationSettings = Seq(
  publishArtifact := false,
  publishArtifact in (Compile, packageBin) := false,
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in (Compile, packageSrc) := false,
  fork := true,
  libraryDependencies ++= Seq(
    `logback-classic`
  )
)

// ======================================================
// Shared code between the recipes and simulator
// ======================================================
lazy val common = (project in file("consul-common"))
  .settings(baseSettings)
  .settings(
    name := "consul-common"
  )

// ======================================================
// The "main" project with the recipes
// ======================================================
lazy val recipes = (project in file("consul-recipes"))
  .settings(baseSettings)
  .settings(
    name := "consul-recipes"
  )
  .dependsOn(common, sim)

// ======================================================
// The Consul simulator
// ======================================================
lazy val sim = (project in file("consul-sim"))
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

// ======================================================
// Only used to run local integration testing for the election
// ======================================================
lazy val integrationElection = (project in file("integration-election"))
  .settings(baseSettings)
  .settings(integrationSettings)
  .settings(
    mainClass in (Compile, run) := Some("org.dmonix.consul.ManualLeaderElection")
  )
  .dependsOn(recipes)

// ======================================================
// Only used to run local integration testing for the Semaphore
// ======================================================
lazy val integrationSemaphore = (project in file("integration-semaphore"))
  .settings(baseSettings)
  .settings(integrationSettings)
  .settings(
    mainClass in (Compile, run) := Some("org.dmonix.consul.ManualSemaphore")
  )
  .dependsOn(recipes)
