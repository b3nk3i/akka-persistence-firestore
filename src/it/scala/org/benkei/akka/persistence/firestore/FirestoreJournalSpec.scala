package org.benkei.akka.persistence.firestore

import com.typesafe.config.ConfigFactory

class FirestoreJournalSpec extends AbstractFirestoreJournalSpec(ConfigFactory.load("application.conf"))
