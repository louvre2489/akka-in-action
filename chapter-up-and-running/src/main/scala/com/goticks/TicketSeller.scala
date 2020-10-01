package com.goticks

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

object TicketSeller {
  def apply(name: String): Behavior[TicketRequest] =
    Behaviors.setup[TicketRequest](context => new TicketSeller(context, name))

  trait TicketRequest
  case class Add(tickets: Vector[Ticket])                               extends TicketRequest
  case class Buy(tickets: Int, replyTo: ActorRef[TicketSeller.Tickets]) extends TicketRequest
  case class GetEvent(replyTo: ActorRef[Option[BoxOffice.Event]])       extends TicketRequest
  case class Cancel(replyTo: ActorRef[Option[BoxOffice.Event]])         extends TicketRequest

  case class Ticket(id: Int)
  case class Tickets(event: String, entries: Vector[Ticket] = Vector.empty[Ticket])
}

class TicketSeller(context: ActorContext[TicketSeller.TicketRequest], event: String)
    extends AbstractBehavior[TicketSeller.TicketRequest](context) {

  import TicketSeller._

  var tickets = Vector.empty[Ticket]

  override def onMessage(msg: TicketSeller.TicketRequest): Behavior[TicketSeller.TicketRequest] = {
    msg match {
      case Add(newTickets) => {
        tickets = tickets ++ newTickets
        this
      }

      case Buy(nrOfTickets, replyTo) =>
        val entries = tickets.take(nrOfTickets)
        if (entries.size >= nrOfTickets) {
          replyTo ! Tickets(event, entries)
          tickets = tickets.drop(nrOfTickets)
        } else {
          replyTo ! Tickets(event)
        }

        this

      case GetEvent(replyTo) =>
        replyTo ! Some(BoxOffice.Event(event, tickets.size))
        this

      case Cancel(replyTo) =>
        replyTo ! Some(BoxOffice.Event(event, tickets.size))
        Behaviors.stopped
    }
  }

}
