package com.stackmob.lucid

/**
 * Copyright 2012 StackMob
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

import java.net.URI
import org.apache.http.client.methods._
import org.apache.http.client.utils.URIBuilder
import org.apache.http.Consts._
import org.apache.http.entity.{BufferedHttpEntity, StringEntity}
import org.apache.http.Header
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.HttpConnectionParams
import org.apache.http.util.EntityUtils
import scalaz._
import scalaz.effects._
import Scalaz._

case class HttpRequest(url: String,
                       params: Map[String, List[String]] = Map(),
                       body: Option[String] = none,
                       headers: Option[NonEmptyList[Header]] = none)

case class HttpResponse(code: Int,
                        body: Option[String] = none,
                        headers: Option[NonEmptyList[Header]] = none)

trait HttpClient {
  def get(request: HttpRequest): IO[HttpResponse]
  def post(request: HttpRequest): IO[HttpResponse]
  def put(request: HttpRequest): IO[HttpResponse]
  def delete(request: HttpRequest): IO[HttpResponse]
}

class ApacheHttpClient extends HttpClient {

  private val connectionTimeoutDefault = 5000
  private val socketTimeoutDefault = 30000

  override def get(request: HttpRequest): IO[HttpResponse] = {
    for {
      get <- (new HttpGet).pure[IO]
      resp <- execute(request, get)
    } yield resp
  }

  override def post(request: HttpRequest): IO[HttpResponse] = {
    for {
      post <- (new HttpPost).pure[IO]
      _ <- post.setEntity(new StringEntity(~request.body, UTF_8)).pure[IO]
      resp <- execute(request, post)
    } yield resp
  }

  override def put(request: HttpRequest): IO[HttpResponse] = {
    for {
      put <- (new HttpPut).pure[IO]
      _ <- put.setEntity(new StringEntity(~request.body, UTF_8)).pure[IO]
      resp <- execute(request, put)
    } yield resp
  }

  override def delete(request: HttpRequest): IO[HttpResponse] = {
    for {
      delete <- (new HttpDelete).pure[IO]
      resp <- execute(request, delete)
    } yield resp
  }

  private def execute(request: HttpRequest, httpMessage: HttpRequestBase): IO[HttpResponse] = io {
    val client = new DefaultHttpClient
    try {
      val httpParams = client.getParams

      request.headers.foreach(_.foreach(httpMessage.addHeader(_)))

      httpMessage.setURI(getURI(request))

      HttpConnectionParams.setConnectionTimeout(httpParams, connectionTimeoutDefault)
      HttpConnectionParams.setSoTimeout(httpParams, socketTimeoutDefault)

      val response = client.execute(httpMessage)
      val entity = Option(response.getEntity).map(new BufferedHttpEntity(_))

      HttpResponse(
        response.getStatusLine.getStatusCode,
        entity.map(EntityUtils.toString(_)),
        Option(response.getAllHeaders).flatMap(_.toList.toNel))
    } finally {
      client.getConnectionManager.shutdown()
    }
  }

  private def getURI(request: HttpRequest): URI = {
    val builder = new URIBuilder(request.url)

    for {
      params <- Option(request.params)
      (param, valueList) <- params
      values <- Option(valueList)
      value <- values
    } builder.addParameter(param, value)

    builder.build()
  }

}
