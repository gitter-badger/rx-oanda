/*
 * Copyright 2015 – 2016 Martin Seeler
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

package rx.oanda

import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.{Flow, Source}
import de.knutwalker.akka.stream.support.CirceStreamSupport
import io.circe.Decoder
import rx.oanda.errors.OandaError._

import scala.util.{Failure, Success, Try}

trait ApiConnection {

  private[oanda] def apiConnections: Flow[(HttpRequest, Long), (Try[HttpResponse], Long), HostConnectionPool]

  private[oanda] def makeRequest[R](req: HttpRequest)(implicit ev: Decoder[R]): Source[R, Unit] =
    Source.single(req → 42L).log("request")
      .via(apiConnections).log("response")
      .flatMapConcat {
        case (Success(HttpResponse(StatusCodes.OK, header, entity, _)), _) ⇒
          entity.dataBytes
            .via(Gzip.decoderFlow)
            .via(CirceStreamSupport.decode[R]).log("decode")
        case (Success(HttpResponse(_, _, entity, _)), _) ⇒ entity.asErrorStream
        case (Failure(e), _) ⇒ Source.failed(e)
        case _ ⇒ Source.empty
      }

}
