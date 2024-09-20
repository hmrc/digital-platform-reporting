/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.util.ByteString
import org.scalatest.EitherValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.test.WireMockSupport

class DownloadConnectorSpec extends AnyFreeSpec
  with Matchers
  with ScalaFutures
  with IntegrationPatience
  with WireMockSupport
  with MockitoSugar
  with EitherValues {

  private lazy val app: Application =
    new GuiceApplicationBuilder().build()

  private lazy val connector = app.injector.instanceOf[DownloadConnector]
  given Materializer = app.materializer

  ".download" - {

    "must return a source of the file contents when the server returns a strict OK" in {

      val contents = "<foo></foo>"

      wireMockServer.stubFor(
        get(urlEqualTo("/some-file.xml"))
          .willReturn(okXml(contents))
      )

      val result = connector.download(url"http://localhost:$wireMockPort/some-file.xml").futureValue
        .runWith(Sink.fold(ByteString.empty)(_ ++ _)).futureValue

      result.utf8String mustEqual contents
    }

    "must fail when the server responds with any other status" in {

      wireMockServer.stubFor(
        get(urlEqualTo("/some-file.xml"))
          .willReturn(aResponse().withStatus(500))
      )

      connector.download(url"http://localhost:$wireMockPort/some-file.xml").failed.futureValue
    }
  }
}
