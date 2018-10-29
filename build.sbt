
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