package com.stackmob.lucid

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

import java.io.File
import java.net.URL
import org.slf4j.LoggerFactory
import org.scalatools.testing.{Event, EventHandler, Logger}
import org.scalatools.testing.Result
import scala.collection.mutable.{Map => MutableMap}
import org.specs2.runner.TestInterfaceRunner
import com.stackmob.lucid.internal.ProvisioningServerSpecs
import scalaz._
import Scalaz._

case class Exit(override val code: Int) extends xsbti.Exit

class LucidRunner extends xsbti.AppMain {
  override def run(config: xsbti.AppConfiguration): xsbti.MainResult = {
    Exit(LucidRunner.run(config.arguments))
  }
}

object LucidRunner {
  var propertyFile = none[URL]
  def run(args: Array[String]): Int = {
    if (args.length > 0) {
      propertyFile = (new File(args(0))).toURI.toURL.some
    }
    if ((new Specs2Runner).runSpecs()) 0 else 1
  }
  def main(args: Array[String]) {
    System.exit(run(args))
  }
}

/**
 * Based on:
 * https://github.com/mmakowski/maven-specs2-plugin/blob/master/src/main/scala/com/mmakowski/maven/plugins/specs2/Specs2Runner.scala
 */
class Specs2Runner {

  private val logger = LoggerFactory.getLogger(classOf[Specs2Runner])

  private class AggregatingHandler extends EventHandler {
    private val testCounts = MutableMap[String, Int]().withDefaultValue(0)
    def report: String = Result.values.map(_.toString).map(s => s + ": " + testCounts(s)).mkString(" " * 3)
    def isNoErrorsOrFailures: Boolean = testCounts(Result.Error.toString) + testCounts(Result.Failure.toString) == 0
    override def handle(event: Event) {
      val resultType = event.result.toString
      testCounts(resultType) = testCounts(resultType) + 1
    }
  }

  private class ScalaToolsLogger extends Logger {
    override def ansiCodesSupported: Boolean = false
    override def error(msg: String) { logger.debug(msg) }
    override def warn(msg: String) { logger.debug(msg) }
    override def info(msg: String) { logger.debug(msg) }
    override def debug(msg: String) { logger.debug(msg) }
    override def trace(t: Throwable) { logger.error(t.getMessage, t) }
  }

  def runSpecs(): Boolean = {
    val classLoader = getClass.getClassLoader
    val runner = new TestInterfaceRunner(classLoader, Array(new ScalaToolsLogger))

    def runWithClassLoader[T](spec: => T): Boolean = {
      class SpecRunner extends Runnable {
        var result = false
        override def run() {
          try {
            spec
            result = true
          } catch {
            case e: Throwable => logger.error(e.getMessage, e)
          }
        }
      }
      val runner = new SpecRunner
      val t = new Thread(runner)
      t.start()
      t.join()
      runner.result
    }

    def runSpec(failingSpecs: List[String], spec: String): List[String] = {
      val handler = new AggregatingHandler
      val isRunCompleted = runWithClassLoader(runner.runSpecification(spec, handler, Array("console")))
      logger.info(handler.report)
      val result = isRunCompleted && handler.isNoErrorsOrFailures
      if (!result) failingSpecs ::: List(spec) else failingSpecs
    }

    val specs = List((new ProvisioningServerSpecs).getClass.getName)
    val failures = specs.foldLeft(List[String]())(runSpec)
    failures.isEmpty
  }

}
