package com.goticks

import scala.concurrent.Future

import akka.actor.typed.{ Behavior, PostStop, Signal }
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success }

object BoxOffice {
  def apply(): Behavior[EventRequest] =
    Behaviors.setup[EventRequest](context => new BoxOffice(context))

  trait EventRequest
  case class CreateEvent(name: String, tickets: Int, replyTo: ActorRef[EventResponse])        extends EventRequest
  case class GetEvent(name: String, replyTo: ActorRef[Option[Event]])                         extends EventRequest
  case class GetEvents(replyTo: ActorRef[EventResponse])                                      extends EventRequest
  case class GetTickets(event: String, tickets: Int, replyTo: ActorRef[TicketSeller.Tickets]) extends EventRequest
  case class CancelEvent(name: String, replyTo: ActorRef[Option[Event]])                      extends EventRequest

  case class Event(name: String, tickets: Int)

  sealed trait EventResponse
  case class EventCreated(event: Event)    extends EventResponse
  case object EventExists                  extends EventResponse
  case class Events(events: Vector[Event]) extends EventResponse
}

class BoxOffice(context: ActorContext[BoxOffice.EventRequest])
    extends AbstractBehavior[BoxOffice.EventRequest](context) {

  import BoxOffice._

  private var ticketSellerActor = Map.empty[String, ActorRef[TicketSeller.TicketRequest]]

  def createTicketSeller(name: String): Behavior[TicketSeller.TicketRequest] =
    TicketSeller.apply(name)

  override def onMessage(msg: BoxOffice.EventRequest): Behavior[BoxOffice.EventRequest] = {
    msg match {
      case CreateEvent(name, tickets, replyTo) =>
        def create() = {
          val eventTickets = context.spawn(createTicketSeller(name), name)
          val newTickets = (1 to tickets).map { ticketId =>
            TicketSeller.Ticket(ticketId)
          }.toVector
          ticketSellerActor += name -> eventTickets
          eventTickets ! TicketSeller.Add(newTickets)
          replyTo ! EventCreated(Event(name, tickets))
        }
        context.child(name).fold(create())(_ => replyTo ! EventExists)
        this

      case GetTickets(event, tickets, replyTo) =>
        def notFound() = replyTo ! TicketSeller.Tickets(event)
        def buy(child: ActorRef[TicketSeller.Buy]) =
          child ! TicketSeller.Buy(tickets, replyTo)

        ticketSellerActor.get(event) match {
          case None        => notFound()
          case Some(actor) => buy(actor)
        }
        this

      case GetEvent(event, replyTo) =>
        def notFound()                                            = replyTo ! None
        def getEvent(child: ActorRef[TicketSeller.TicketRequest]) = child ! TicketSeller.GetEvent(replyTo)

        ticketSellerActor.get(event) match {
          case None        => notFound()
          case Some(actor) => getEvent(actor)
        }
        this

      case GetEvents(replyTo) =>
        implicit val ec = context.executionContext

        def getEvents = context.children.map { child =>
          val system             = context.system
          implicit val timeout   = Timeout(1.second)
          implicit val scheduler = system.scheduler

          context.self.ask(r => GetEvent(child.path.name, r)).mapTo[Option[Event]]
        }
        def convertToEvents(f: Future[Iterable[Option[Event]]]) =
          f.map(_.flatten).map(l => Events(l.toVector))

        val f = convertToEvents(Future.sequence(getEvents)(Vector, implicitly))
        f.onComplete {
          case Success(res) => replyTo ! res
          case Failure(e)   => throw e
        }
        this

      case CancelEvent(event, replyTo) =>
        def notFound()                                               = replyTo ! None
        def cancelEvent(child: ActorRef[TicketSeller.TicketRequest]) = child ! TicketSeller.Cancel(replyTo)

        ticketSellerActor.get(event) match {
          case None        => notFound()
          case Some(actor) => cancelEvent(actor)
        }
        this
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[BoxOffice.EventRequest]] = {
    case PostStop =>
      context.log.info("Application stopped")
      this
  }
}
