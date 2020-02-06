publishArtifact := false


val `specs-core-version` = "4.8.3"
val `akka-version` = "2.5.29"
val `akka-http-version` = "10.1.11"

val baseSettings = Seq(
  organization := "org.dmonix",
  version := "0.4.0",
  scalaVersion := "2.12.0",
  crossScalaVersions := Seq("2.11.12", "2.12.0"),
  scalacOptions := Seq("-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-unchecked",
    "-deprecation",
    "-encoding", "utf8"),
  libraryDependencies ++= Seq(
    "io.spray" %%  "spray-json"  % "1.3.5",
    "org.slf4j" % "slf4j-api" % "1.7.30",
    "com.typesafe.akka" %% "akka-actor" % `akka-version`,
    "com.typesafe.akka" %% "akka-stream"  % `akka-version`,
    "org.specs2" %% "specs2-core" % `specs-core-version` % "test",
    "org.specs2" %% "specs2-mock" % `specs-core-version` % "test",
    "org.specs2" %% "specs2-junit" % `specs-core-version` % "test",
    "org.specs2" %% "specs2-matcher-extra" % `specs-core-version` % "test",
    "ch.qos.logback" % "logback-classic" % "1.2.3" % "test"
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
      "com.typesafe.akka" %% "akka-http" % `akka-http-version`,
      "com.typesafe.akka" %% "akka-stream-testkit" % `akka-version` % "test",
      "com.typesafe.akka" %% "akka-http-testkit" % `akka-http-version` % "test"
    )
  )
  .dependsOn(common)
