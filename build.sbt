name := "consul-recipes"

organization := "org.dmonix"
version := "0.4.0"
scalaVersion := "2.12.8"
crossScalaVersions := Seq("2.11.12", "2.12.8")

scalacOptions := Seq("-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-unchecked",
    "-deprecation",
    "-encoding", "utf8")

val `specs-core-version` = "4.3.4"
val `akka-version` = "2.5.19"
libraryDependencies ++= Seq(
    "io.spray" %%  "spray-json"  % "1.3.4",
    "org.slf4j" % "slf4j-api" % "1.7.25",
    "org.specs2" %% "specs2-core" % `specs-core-version` % "test",
    "org.specs2" %% "specs2-mock" % `specs-core-version` % "test", 
    "org.specs2" %% "specs2-junit" % `specs-core-version` % "test",
    "org.specs2" %% "specs2-matcher-extra" % `specs-core-version` % "test",
    "com.typesafe.akka" %% "akka-http" % "10.1.7" % "test",
    "com.typesafe.akka" %% "akka-actor" % `akka-version` % "test",
    "com.typesafe.akka" %% "akka-stream" % `akka-version` % "test",
    "ch.qos.logback" % "logback-classic" % "1.2.3" % "test"
)

