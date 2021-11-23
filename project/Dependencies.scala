import sbt._

object Dependencies extends AutoPlugin {
  object autoImport {

    val `spray-json` = "io.spray" %% "spray-json" % "1.3.6"

    val `slf4j-api` = "org.slf4j" % "slf4j-api" % "1.7.32"
    val `logback-classic` = "ch.qos.logback" % "logback-classic" % "1.2.7"

    val `specs-core-version` = "4.10.6"
    val `specs2-core` = "org.specs2" %% "specs2-core" % `specs-core-version`
    val `specs2-mock` = "org.specs2" %% "specs2-mock" % `specs-core-version`
    val `specs2-junit` = "org.specs2" %% "specs2-junit" % `specs-core-version`
    val `specs2-matcher-extra` = "org.specs2" %% "specs2-matcher-extra" % `specs-core-version`

    val `akka-version` = "2.5.32"
    val `akka-http-version` = "10.1.14"
    val `akka-http` = "com.typesafe.akka" %% "akka-http" % `akka-http-version`
    val `akka-actor` = "com.typesafe.akka" %% "akka-actor" % `akka-version`
    val `akka-stream` = "com.typesafe.akka" %% "akka-stream" % `akka-version`

    val `akka-stream-testkit` = "com.typesafe.akka" %% "akka-stream-testkit" % `akka-version`
    val `akka-http-testkit` = "com.typesafe.akka" %% "akka-http-testkit" % `akka-http-version`

  }
}
