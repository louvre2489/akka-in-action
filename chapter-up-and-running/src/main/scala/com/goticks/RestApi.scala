package com.goticks

import akka.actor._
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.{ ActorRef => AR }
import akka.actor.{ ActorSystem => AS }
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{ ExecutionContext, Future }

class RestApi(system: AS, timeout: Timeout) extends RestRoutes {
  implicit val requestTimeout   = timeout
  implicit def executionContext = system.dispatcher

  // カッコがないと以下のエラーが発生するのでカッコを付ける
  // method without a parameter list overrides a method with a single empty one
//  def createBoxOffice()      = system.actorOf(BoxOffice.props, BoxOffice.name)
  def createBoxOfficeTyped() = BoxOfficeTyped()
}

trait RestRoutes extends BoxOfficeTypedApi with EventTypedMarshalling {
  import StatusCodes._

  def routes: Route =
    eventsRoute ~
    eventRoute //~
//      ticketsRoute

  def eventsRoute =
    pathPrefix("events") {
      pathEndOrSingleSlash {
        get {
          // GET /events
          onSuccess(getEvents()) { events =>
            complete(OK, events)
          }
        }
      }
    }

  def eventRoute =
    pathPrefix("events" / Segment) { event =>
      pathEndOrSingleSlash {
        post {
          // POST /events/:event
          entity(as[EventDescription]) { ed =>
            onSuccess(createEvent(event, ed.tickets)) {
              case BoxOfficeTyped.EventCreated(event) => complete(Created, event)
              case BoxOfficeTyped.EventExists =>
                val err = Error(s"$event event exists already.")
                complete(BadRequest, err)
              case _ => throw new RuntimeException  // BoxOfficeのレスポンス型を別Behaviorに分けたらこんなmatchしなくて済むんだけど・・・

            }
          }
        } // ~
//        get {
//          // GET /events/:event
//          onSuccess(getEvent(event)) {
//            _.fold(complete(NotFound))(e => complete(OK, e))
//          }
//        } ~
//        delete {
//          // DELETE /events/:event
//          onSuccess(cancelEvent(event)) {
//            _.fold(complete(NotFound))(e => complete(OK, e))
//          }
//        }
      }
    }

// def ticketsRoute =
//   pathPrefix("events" / Segment / "tickets") { event =>
//     post {
//       pathEndOrSingleSlash {
//         // POST /events/:event/tickets
//         entity(as[TicketRequest]) { request =>
//           onSuccess(requestTickets(event, request.tickets)) { tickets =>
//             if (tickets.entries.isEmpty) complete(NotFound)
//             else complete(Created, tickets)
//           }
//         }
//       }
//     }
//   }

}

trait BoxOfficeTypedApi {
  import BoxOfficeTyped._

  def createBoxOfficeTyped(): Behavior[BoxOfficeTyped.EventRequest]

  implicit def executionContext: ExecutionContext
  implicit def requestTimeout: Timeout

//  lazy val boxOffice = createBoxOfficeTyped()

  val boxOffice          = ActorSystem(BoxOfficeTyped(), "boxOffice")
  implicit val scheduler = boxOffice.scheduler

  def createEvent(event: String, nrOfTickets: Int): Future[EventResponse] =
    boxOffice
      .ask(replyTo => CreateEvent(event, nrOfTickets, replyTo))
      .mapTo[EventResponse]

  def getEvents(): Future[Events] =
    boxOffice.ask(replyTo => GetEvents(replyTo)).mapTo[Events]
}

trait BoxOfficeApi {
  import BoxOffice._

  def createBoxOffice(): AR

  implicit def executionContext: ExecutionContext
  implicit def requestTimeout: Timeout

  lazy val boxOffice = createBoxOffice()

  def createEvent(event: String, nrOfTickets: Int) =
    boxOffice
      .ask(CreateEvent(event, nrOfTickets))
      .mapTo[EventResponse]

  def getEvents() =
    boxOffice.ask(GetEvents).mapTo[Events]

  def getEvent(event: String) =
    boxOffice
      .ask(GetEvent(event))
      .mapTo[Option[Event]]

  def cancelEvent(event: String) =
    boxOffice
      .ask(CancelEvent(event))
      .mapTo[Option[Event]]

  def requestTickets(event: String, tickets: Int) =
    boxOffice
      .ask(GetTickets(event, tickets))
      .mapTo[TicketSeller.Tickets]
}
//
