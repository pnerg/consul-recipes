name := "consul-recipes"

organization := "org.dmonix"
version := "0.3.1"
scalaVersion := "2.12.0"
crossScalaVersions := Seq("2.11.12", "2.12.0")

scalacOptions := Seq("-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-unchecked",
    "-deprecation",
    "-encoding", "utf8")

val `specs-core-version` = "4.3.4"
libraryDependencies ++= Seq(
    "io.spray" %%  "spray-json"  % "1.3.4",
    "org.slf4j" % "slf4j-api" % "1.7.25",
    "org.specs2" %% "specs2-core" % `specs-core-version` % "test",
    "org.specs2" %% "specs2-mock" % `specs-core-version` % "test", 
    "org.specs2" %% "specs2-junit" % `specs-core-version` % "test",
    "org.specs2" %% "specs2-matcher-extra" % `specs-core-version` % "test",
    "com.typesafe.akka" %% "akka-http" % "10.0.11" % "test",
    "ch.qos.logback" % "logback-classic" % "1.2.3" % "test"
)

