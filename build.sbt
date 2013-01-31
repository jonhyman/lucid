import net.virtualvoid.sbt.graph.Plugin
import org.scalastyle.sbt.ScalastylePlugin
import sbtrelease._
import ReleaseKeys._
import ReleaseStateTransformations._
import LucidReleaseSteps._

name := "lucid"

organization := "com.stackmob"

scalaVersion := "2.9.2"

crossScalaVersions := Seq("2.9.2")

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

ScalastylePlugin.Settings

Plugin.graphSettings

seq(conscriptSettings: _*)

releaseSettings

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  setLaunchConfigReleaseVersion,
  setReadmeReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  setLaunchConfigNextVersion,
  pushChanges
)

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
  } else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/stackmob/lucid</url>
  <licenses>
    <license>	
      <name>Apache 2</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:stackmob/lucid.git</url>
    <connection>scm:git:git@github.com:stackmob/lucid.git</connection>
  </scm>
  <developers>
    <developer>
      <id>taylorleese</id>
      <name>Taylor Leese</name>
      <url>http://www.stackmob.com</url>
    </developer>
  </developers>
)
