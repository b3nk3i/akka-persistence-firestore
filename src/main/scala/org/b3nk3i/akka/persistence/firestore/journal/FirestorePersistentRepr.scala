package org.b3nk3i.akka.persistence.firestore.journal

import org.b3nk3i.akka.persistence.firestore.data.Document.Document

case class FirestorePersistentRepr(persistenceId: String, sequence: Long, data: Document)
