/**
 * Copyright 2012-2013 StackMob
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.Source
import java.io.PrintWriter
import sbtrelease._
import ReleasePlugin._
import ReleaseKeys._
import sbt._

object LucidReleaseSteps {

  val launchConfig = "src/main/conscript/lucid/launchconfig"
  val readme = "README.md"

  lazy val setReadmeReleaseVersion: ReleaseStep = { st: State =>
    val releaseVersions = getReleasedVersion(st)
    updateReadme(st, releaseVersions._1)
    commitReadme(st, releaseVersions._1)
    st
  }

  lazy val setLaunchConfigReleaseVersion: ReleaseStep = { st: State =>
    val releaseVersions = getReleasedVersion(st)
    updateLaunchConfig(st, "%s-SNAPSHOT".format(releaseVersions._1), releaseVersions._1)
    commitLaunchConfig(st, releaseVersions._1)
    st
  }

  lazy val setLaunchConfigNextVersion: ReleaseStep = { st: State =>
    val releaseVersions = getReleasedVersion(st)
    updateLaunchConfig(st, releaseVersions._1, releaseVersions._2)
    commitLaunchConfig(st, releaseVersions._2)
    st
  }

  private def getReleasedVersion(st: State): (String, String) = {
    st.get(versions).getOrElse(sys.error("No versions are set."))
  }

  private def updateLaunchConfig(st: State, oldVersion: String, newVersion: String) {
    val oldLaunchConfig = Source.fromFile(launchConfig).mkString
    val out = new PrintWriter(launchConfig, "UTF-8")
    try {
      val newLaunchConfig = oldLaunchConfig.replaceAll(oldVersion, newVersion)
      newLaunchConfig.foreach(out.write(_))
    } finally {
      out.close()
    }
  }

  private def updateReadme(st: State, newVersion: String) {
    val conscriptR = """stackmob/lucid/\d+\.\d+\.\d+""".r
    val oldReadme = Source.fromFile(readme).mkString
    val out = new PrintWriter(readme, "UTF-8")
    try {
      val newReadme = conscriptR.replaceFirstIn(oldReadme, "stackmob/lucid/%s".format(newVersion))
      newReadme.foreach(out.write(_))
    } finally {
      out.close()
    }
  }

  private def commitLaunchConfig(st: State, newVersion: String) {
    val vcs = Project.extract(st).get(versionControlSystem).getOrElse(sys.error("Unable to get version control system."))
    vcs.add(launchConfig) !! st.log
    vcs.commit("launchconfig updated to %s".format(newVersion)) ! st.log
  }

  private def commitReadme(st: State, newVersion: String) {
    val vcs = Project.extract(st).get(versionControlSystem).getOrElse(sys.error("Unable to get version control system."))
    vcs.add(readme) !! st.log
    vcs.commit("README.md updated to %s".format(newVersion)) ! st.log
  }

}
