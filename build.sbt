
name := "consul-recipes"

organization := "org.dmonix"
version := "0.3.0"
scalaVersion := "2.11.12"
crossScalaVersions := Seq("2.11.12", "2.12.0")

scalacOptions := Seq("-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-unchecked",
    "-deprecation",
    "-encoding", "utf8")
libraryDependencies ++= Seq(
  "io.spray" %%  "spray-json"  % "1.3.4"
)

