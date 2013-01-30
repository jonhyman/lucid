name := "lucid"

organization := "com.stackmob"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.9.2"

scalacOptions ++= Seq("-unchecked", "-deprecation")

libraryDependencies ++= {
  val httpVersion = "4.2.1"
  Seq(
    "org.scalaz" %% "scalaz-core" % "6.0.4",
    "net.liftweb" %% "lift-json-scalaz" % "2.5-M4",
    "commons-codec" % "commons-codec" % "1.7",
    "commons-lang" % "commons-lang" % "2.6",
    "commons-validator" % "commons-validator" % "1.4.0",
    "org.apache.httpcomponents" % "httpcore" % httpVersion,
    "org.apache.httpcomponents" % "httpclient" % httpVersion,
    "ch.qos.logback" % "logback-classic" % "1.0.9",
    "org.slf4j" % "slf4j-api" % "1.7.2",
    "org.slf4j" % "jul-to-slf4j" % "1.7.2" % "runtime",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.2" % "runtime",
    "org.slf4j" % "log4j-over-slf4j" % "1.7.2" % "runtime",
    "org.scalacheck" %% "scalacheck" % "1.10.0",
    "org.specs2" %% "specs2" % "1.12.3",
    "org.mockito" % "mockito-all" % "1.9.5",
    "org.specs2" %% "specs2-scalaz-core" % "6.0.1"
  )
}

logBuffered := false

net.virtualvoid.sbt.graph.Plugin.graphSettings

seq(conscriptSettings: _*)
