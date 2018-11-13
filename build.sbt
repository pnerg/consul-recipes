
name := "consul-recipes"

organization := "org.dmonix"
version := "0.3.0"
scalaVersion := "2.12.0"
crossScalaVersions := Seq("2.11.12", "2.12.0")

scalacOptions := Seq("-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-unchecked",
    "-deprecation",
    "-encoding", "utf8")

val specs_core_ver = "4.3.4"
libraryDependencies ++= Seq(
    "io.spray" %%  "spray-json"  % "1.3.4",
    "org.slf4j" % "slf4j-api" % "1.7.25",
    "org.specs2" %% "specs2-core" % specs_core_ver % "test",
    "org.specs2" %% "specs2-mock" % specs_core_ver % "test", 
    "org.specs2" %% "specs2-junit" % specs_core_ver % "test",
    "org.specs2" %% "specs2-matcher-extra" % specs_core_ver % "test",
    "com.typesafe.akka" %% "akka-http" % "10.0.11" % "test",
    "ch.qos.logback" % "logback-classic" % "1.2.3" % "test"
)

//----------------------------
//needed to create the proper pom.xml for publishing to mvn central
//----------------------------
publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { _ => false }
pomExtra := (
  <url>https://github.com/pnerg/consul-recipes</url>
    <licenses>
        <license>
            <name>Apache</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
    </licenses>
    <scm>
        <url>git@github.com:pnerg/consul-recipes.git</url>
        <connection>scm:git:git@github.com/pnerg/consul-recipes.git</connection>
    </scm>
    <developers>
        <developer>
            <id>pnerg</id>
            <name>Peter Nerg</name>
            <url>http://github.com/pnerg</url>
        </developer>
    </developers>)