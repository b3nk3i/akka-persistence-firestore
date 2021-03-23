package org.benkei.akka.persistence.firestore.query

import akka.persistence.journal.{EventSeq, ReadEventAdapter, Tagged, WriteEventAdapter}

object EventAdapterTest {
  case class Event(value: String) {
    def adapted: EventAdapted = EventAdapted(value)
  }

  case class TaggedEvent(event: Event, tag: String)

  case class TaggedAsyncEvent(event: Event, tag: String)

  case class EventAdapted(value: String) {
    def restored: EventRestored = EventRestored(value)
  }

  case class EventRestored(value: String)

  class TestReadEventAdapter extends ReadEventAdapter {
    override def fromJournal(event: Any, manifest: String): EventSeq =
      event match {
        case e: EventAdapted => EventSeq.single(e.restored)
      }
  }

  class TestWriteEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = ""

    override def toJournal(event: Any): Any =
      event match {
        case e: Event => e.adapted
        case TaggedEvent(e: Event, tag) => Tagged(e.adapted, Set(tag))
        case TaggedAsyncEvent(e: Event, tag) => Tagged(e.adapted, Set(tag))
        case _ => event
      }
  }
}
