package com.goticks

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.{ ActorSystem => AS }
import akka.actor.typed.{ ActorSystem, Behavior }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.util.Timeout

import scala.concurrent.{ ExecutionContext, Future }

class RestApi(system: AS, timeout: Timeout) extends RestRoutes {
  implicit val requestTimeout   = timeout
  implicit def executionContext = system.dispatcher

  def createBoxOffice() = BoxOffice()
}

trait RestRoutes extends BoxOfficeApi with EventTypedMarshalling {
  import StatusCodes._

  def routes: Route =
    eventsRoute ~
    eventRoute ~
    ticketsRoute

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
              case BoxOffice.EventCreated(event) => complete(Created, event)
              case BoxOffice.EventExists =>
                val err = Error(s"$event event exists already.")
                complete(BadRequest, err)
              case _ => throw new RuntimeException // BoxOfficeのレスポンス型を別Behaviorに分けたらこんなmatchしなくて済むんだけど・・・

            }
          }
        } ~
        get {
          // GET /events/:event
          onSuccess(getEvent(event)) {
            _.fold(complete(NotFound))(e => complete(OK, e))
          }
        } ~
        delete {
          // DELETE /events/:event
          onSuccess(cancelEvent(event)) {
            _.fold(complete(NotFound))(e => complete(OK, e))
          }
        }
      }
    }

  def ticketsRoute =
    pathPrefix("events" / Segment / "tickets") { event =>
      post {
        pathEndOrSingleSlash {
          // POST /events/:event/tickets
          entity(as[TicketRequest]) { request =>
            onSuccess(requestTickets(event, request.tickets)) { tickets =>
              if (tickets.entries.isEmpty) complete(NotFound)
              else complete(Created, tickets)
            }
          }
        }
      }
    }

}

trait BoxOfficeApi {
  import BoxOffice._

  def createBoxOffice(): Behavior[BoxOffice.EventRequest]

  implicit def executionContext: ExecutionContext
  implicit def requestTimeout: Timeout

  val boxOffice          = ActorSystem(BoxOffice(), "boxOffice")
  implicit val scheduler = boxOffice.scheduler

  def createEvent(event: String, nrOfTickets: Int): Future[EventResponse] =
    boxOffice
      .ask(replyTo => CreateEvent(event, nrOfTickets, replyTo))
      .mapTo[EventResponse]

  def getEvents(): Future[Events] =
    boxOffice.ask(replyTo => GetEvents(replyTo)).mapTo[Events]

  def getEvent(event: String) =
    boxOffice
      .ask(replyTo => GetEvent(event, replyTo))
      .mapTo[Option[Event]]

  def cancelEvent(event: String) =
    boxOffice
      .ask(replyTo => CancelEvent(event, replyTo))
      .mapTo[Option[Event]]

  def requestTickets(event: String, tickets: Int) =
    boxOffice
      .ask(replyTo => GetTickets(event, tickets, replyTo))
      .mapTo[TicketSeller.Tickets]
}
