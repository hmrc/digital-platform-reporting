package connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import services.UuidService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport
import utils.DateTimeFormats.RFC7231Formatter

import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID

class SubmissionConnectorSpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WireMockSupport
    with MockitoSugar
    with EitherValues
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach {

  private val now = Instant.now()
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val mockUuidService = mock[UuidService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockUuidService)
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.report-submission.port" -> wireMockPort,
        "microservice.services.report-submission.bearer-token" -> "token"
      )
      .overrides(
        bind[Clock].toInstance(clock),
        bind[UuidService].toInstance(mockUuidService)
      )
      .build()

  private lazy val connector: SubmissionConnector = app.injector.instanceOf[SubmissionConnector]

  given Materializer = app.materializer

  "submit" - {

    val submissionId = "submissionId"
    val correlationId = UUID.randomUUID().toString
    val expectedDate = RFC7231Formatter.format(now.atZone(ZoneOffset.UTC))

    "must return successfully when the server responds with NO_CONTENT" in {

      wireMockServer.stubFor(
        post(urlPathEqualTo("/digital-platform-reporting-stubs/dac6/dprs0502/v1"))
          .withRequestBody(equalTo("foobar"))
          .withHeader("Authorization", equalTo("Bearer token"))
          .withHeader("X-Forwarded-Host", equalTo("digital-platform-reporting"))
          .withHeader("X-Correlation-ID", equalTo(correlationId))
          .withHeader("X-Conversation-ID", equalTo(submissionId))
          .withHeader("Content-Type", equalTo("application/xml"))
          .withHeader("Accept", equalTo("application/xml"))
          .withHeader("Date", equalTo(expectedDate))
          .willReturn(noContent())
      )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val body = Source(Seq(ByteString.fromString("foo"), ByteString.fromString("bar")))
      connector.submit(submissionId, body)(using HeaderCarrier()).futureValue
    }

    "must fail when the server responds with anything else" in {

      wireMockServer.stubFor(
        post(urlPathEqualTo("/dac6/dprs0502/v1"))
          .withRequestBody(equalTo("foobar"))
          .withHeader("Authorization", equalTo("Bearer token"))
          .withHeader("X-Forwarded-Host", equalTo("digital-platform-submission"))
          .withHeader("X-Correlation-ID", equalTo(correlationId))
          .withHeader("X-Correlation-ID", equalTo(correlationId))
          .withHeader("X-Conversation-ID", equalTo(submissionId))
          .withHeader("Content-Type", equalTo("application/xml"))
          .withHeader("Accept", equalTo("application/xml"))
          .withHeader("Date", equalTo(expectedDate))
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val body = Source(Seq(ByteString.fromString("foo"), ByteString.fromString("bar")))
      connector.submit(submissionId, body)(using HeaderCarrier()).failed.futureValue
    }
  }
}
