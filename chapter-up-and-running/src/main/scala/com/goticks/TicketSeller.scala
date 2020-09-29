package com.goticks

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.{Actor, PoisonPill, Props, ActorContext => AC}
import com.goticks.BoxOfficeTyped.EventResponse
import com.goticks.TicketSellerTyped.{Add, Ticket}

object TicketSellerTyped {
  def apply(name: String): Behavior[TicketRequest] =
    Behaviors.setup[TicketRequest](context => new TicketSellerTyped(context, name))

  trait TicketRequest
  case class Add(tickets: Vector[Ticket]) extends TicketRequest
  case class GetEvent(replyTo: ActorRef[Option[BoxOfficeTyped.Event]]) extends TicketRequest

  case class Ticket(id: Int)
  case class Tickets(event: String,
                     entries: Vector[Ticket] = Vector.empty[Ticket])
}

class TicketSellerTyped(context: ActorContext[TicketSellerTyped.TicketRequest], event: String) extends AbstractBehavior[TicketSellerTyped.TicketRequest](context) {

  import TicketSellerTyped._

  var tickets = Vector.empty[Ticket]

  override def onMessage(msg: TicketSellerTyped.TicketRequest): Behavior[TicketSellerTyped.TicketRequest] = {
    msg match {
      case Add(newTickets) => {
        tickets = tickets ++ newTickets
        this
      }
      case GetEvent(replyTo) =>
        replyTo ! Some(BoxOfficeTyped.Event(event, tickets.size))
        this
    }
 }

}

object TicketSeller {
  def props(event: String) = Props(new TicketSeller(event))

  case class Add(tickets: Vector[Ticket])
  case class Buy(tickets: Int)
  case class Ticket(id: Int)
  case class Tickets(event: String,
                     entries: Vector[Ticket] = Vector.empty[Ticket])
  case object GetEvent
  case object Cancel

}

class TicketSeller(event: String) extends Actor {
  import TicketSeller._
  import com.goticks.TicketSeller.{Add, Ticket}

  var tickets = Vector.empty[Ticket]

  def receive = {
//    case Add(newTickets) => tickets = tickets ++ newTickets
    case Buy(nrOfTickets) =>
      val entries = tickets.take(nrOfTickets)
      if(entries.size >= nrOfTickets) {
        sender() ! Tickets(event, entries)
        tickets = tickets.drop(nrOfTickets)
      } else sender() ! Tickets(event)
    case GetEvent => sender() ! Some(BoxOffice.Event(event, tickets.size))
    case Cancel =>
      sender() ! Some(BoxOffice.Event(event, tickets.size))
      self ! PoisonPill
  }
}
