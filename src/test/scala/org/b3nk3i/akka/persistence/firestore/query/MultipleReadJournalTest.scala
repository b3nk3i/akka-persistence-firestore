package org.b3nk3i.akka.persistence.firestore.query

import akka.persistence.query.{NoOffset, PersistenceQuery}
import akka.stream.scaladsl.Sink
import org.b3nk3i.akka.persistence.firestore.query.EventsByTagTest.configOverrides
import org.b3nk3i.akka.persistence.firestore.query.scaladsl.FirestoreReadJournal

abstract class MultipleReadJournalTest extends QueryTestSpec {

  it should "be able to create two read journals and use eventsByTag on them" in
    withActorSystem(config, configOverrides) { implicit system =>
      val normalReadJournal =
        PersistenceQuery(system).readJournalFor[FirestoreReadJournal](FirestoreReadJournal.Identifier)
      val secondReadJournal =
        PersistenceQuery(system).readJournalFor[FirestoreReadJournal]("firestore-read-journal-2")

      val events1 = normalReadJournal.currentEventsByTag("someTag", NoOffset).runWith(Sink.seq)
      val events2 = secondReadJournal.currentEventsByTag("someTag", NoOffset).runWith(Sink.seq)
      events1.futureValue shouldBe empty
      events2.futureValue shouldBe empty
    }
}
