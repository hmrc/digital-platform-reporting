package connectors

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.{HttpClientV2, given}

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DownloadConnector @Inject() (
                                    httpClient: HttpClientV2
                                  )(using ExecutionContext, Materializer) {

  def download(url: URL): Future[Source[ByteString, _]] =
    httpClient.get(url)(using HeaderCarrier()).stream[Source[ByteString, _]]
}
