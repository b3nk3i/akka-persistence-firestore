package org.b3nk3i.akka.persistence.firestore.query

import akka.actor.ExtendedActorSystem
import akka.persistence.query.ReadJournalProvider
import com.typesafe.config.Config

class FirestoreReadJournalProvider(system: ExtendedActorSystem, config: Config, configPath: String)
    extends ReadJournalProvider {

  override val scaladslReadJournal: akka.persistence.query.scaladsl.ReadJournal =
    new scaladsl.FirestoreReadJournal(config, configPath)(system)

  override val javadslReadJournal: akka.persistence.query.javadsl.ReadJournal =
    new javadsl.FirestoreReadJournal(new scaladsl.FirestoreReadJournal(config, configPath)(system))
}
