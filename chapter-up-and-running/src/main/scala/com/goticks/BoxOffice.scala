package com.goticks

import scala.concurrent.Future
import akka.actor._
import akka.actor.{ActorRef => AR}
import akka.actor.typed.{Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object BoxOfficeTyped {
  def apply(): Behavior[EventRequest] =
    Behaviors.setup[EventRequest](context => new BoxOfficeTyped(context))

  trait EventRequest
  case class CreateEvent(name: String, tickets: Int, replyTo: ActorRef[EventResponse]) extends EventRequest
  case class GetEvent(name: String, replyTo: ActorRef[Option[Event]])                  extends EventRequest
  case class GetEvents(replyTo: ActorRef[EventResponse])                               extends EventRequest

  case class Event(name: String, tickets: Int)

  sealed trait EventResponse
  case class EventCreated(event: Event)    extends EventResponse
  case object EventExists                  extends EventResponse
  case class Events(events: Vector[Event]) extends EventResponse
}

class BoxOfficeTyped(context: ActorContext[BoxOfficeTyped.EventRequest])
    extends AbstractBehavior[BoxOfficeTyped.EventRequest](context) {

  import BoxOfficeTyped._

  private var ticketSellerActor = Map.empty[String, ActorRef[TicketSellerTyped.TicketRequest]]

  def createTicketSeller(name: String): Behavior[TicketSellerTyped.TicketRequest] =
    TicketSellerTyped.apply(name)

  override def onMessage(msg: BoxOfficeTyped.EventRequest): Behavior[BoxOfficeTyped.EventRequest] = {
    msg match {
      case CreateEvent(name, tickets, replyTo) =>
        def create() = {
          val eventTickets = context.spawn(createTicketSeller(name), name)
          val newTickets = (1 to tickets).map { ticketId =>
            TicketSellerTyped.Ticket(ticketId)
          }.toVector
          ticketSellerActor += name -> eventTickets
          eventTickets ! TicketSellerTyped.Add(newTickets)
          replyTo ! EventCreated(Event(name, tickets))
        }
        context.child(name).fold(create())(_ => replyTo ! EventExists)
        this

      case GetEvent(event, replyTo) =>
        def notFound()                                           = replyTo ! None
        def getEvent(child: ActorRef[TicketSellerTyped.TicketRequest]) = child ! TicketSellerTyped.GetEvent(replyTo)

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
          case Success(s) => replyTo ! s
          case Failure(e) => throw e
        }
        this
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[BoxOfficeTyped.EventRequest]] = {
    case PostStop =>
      context.log.info("Application stopped")
      this
  }
}

object BoxOffice {
  def props(implicit timeout: Timeout) = Props(new BoxOffice)
  def name                             = "boxOffice"

  case class CreateEvent(name: String, tickets: Int)
  case class GetEvent(name: String)
  case object GetEvents
  case class GetTickets(event: String, tickets: Int)
  case class CancelEvent(name: String)

  case class Event(name: String, tickets: Int)
  case class Events(events: Vector[Event])

  sealed trait EventResponse
  case class EventCreated(event: Event) extends EventResponse
  case object EventExists               extends EventResponse

}

class BoxOffice(implicit timeout: Timeout) extends Actor {
  import BoxOffice._
  import context._

  def createTicketSeller(name: String) =
    context.actorOf(TicketSeller.props(name), name)

  def receive = {
//    case CreateEvent(name, tickets) =>
//      def create() = {
//        val eventTickets = createTicketSeller(name)
//        val newTickets = (1 to tickets).map { ticketId =>
//          TicketSeller.Ticket(ticketId)
//        }.toVector
//        eventTickets ! TicketSeller.Add(newTickets)
//        sender() ! EventCreated(Event(name, tickets))
//      }
//      context.child(name).fold(create())(_ => sender() ! EventExists)

    case GetTickets(event, tickets) =>
      def notFound() = sender() ! TicketSeller.Tickets(event)
      def buy(child: AR) =
        child.forward(TicketSeller.Buy(tickets))

      context.child(event).fold(notFound())(buy)

    case GetEvent(event) =>
      def notFound()          = sender() ! None
      def getEvent(child: AR) = child forward TicketSeller.GetEvent
      context.child(event).fold(notFound())(getEvent)

    case GetEvents =>
      import akka.pattern.ask
      import akka.pattern.pipe

      def getEvents = context.children.map { child =>
        self.ask(GetEvent(child.path.name)).mapTo[Option[Event]]
      }
      def convertToEvents(f: Future[Iterable[Option[Event]]]) =
        f.map(_.flatten).map(l => Events(l.toVector))

      pipe(convertToEvents(Future.sequence(getEvents))) to sender()

    case CancelEvent(event) =>
      def notFound()             = sender() ! None
      def cancelEvent(child: AR) = child forward TicketSeller.Cancel
      context.child(event).fold(notFound())(cancelEvent)
  }
}
