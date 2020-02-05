publishArtifact := false


val `specs-core-version` = "4.3.4"
val baseSettings = Seq(
  organization := "org.dmonix",
  version := "0.4.0-SNAPSHOT",
  scalaVersion := "2.12.0",
  crossScalaVersions := Seq("2.11.12", "2.12.0"),
  scalacOptions := Seq("-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-unchecked",
    "-deprecation",
    "-encoding", "utf8"),
  libraryDependencies ++= Seq(
    "io.spray" %%  "spray-json"  % "1.3.4",
    "org.slf4j" % "slf4j-api" % "1.7.25",
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
      "com.typesafe.akka" %% "akka-http" % "10.0.11"
    )
  )
  .dependsOn(common)
