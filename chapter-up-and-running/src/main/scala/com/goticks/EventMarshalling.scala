package com.goticks

import spray.json._

case class EventDescription(tickets: Int) {
  require(tickets > 0)
}

case class TicketRequest(tickets: Int) {
  require(tickets > 0)
}

case class Error(message: String)


trait EventTypedMarshalling extends DefaultJsonProtocol {
  import BoxOfficeTyped._

  implicit val eventDescriptionFormat = jsonFormat1(EventDescription)
  implicit val eventFormat = jsonFormat2(Event)
  implicit val eventsFormat = jsonFormat1(Events)
  implicit val ticketRequestFormat = jsonFormat1(TicketRequest)
  implicit val ticketFormat = jsonFormat1(TicketSellerTyped.Ticket)
  implicit val ticketsFormat = jsonFormat2(TicketSellerTyped.Tickets)
  implicit val errorFormat = jsonFormat1(Error)
}


trait EventMarshalling  extends DefaultJsonProtocol {
  import BoxOffice._

  implicit val eventDescriptionFormat = jsonFormat1(EventDescription)
  implicit val eventFormat = jsonFormat2(Event)
  implicit val eventsFormat = jsonFormat1(Events)
  implicit val ticketRequestFormat = jsonFormat1(TicketRequest)
  implicit val ticketFormat = jsonFormat1(TicketSeller.Ticket)
  implicit val ticketsFormat = jsonFormat2(TicketSeller.Tickets)
  implicit val errorFormat = jsonFormat1(Error)
}
