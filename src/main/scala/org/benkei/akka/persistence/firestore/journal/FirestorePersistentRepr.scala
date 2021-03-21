package org.benkei.akka.persistence.firestore.journal

import org.benkei.akka.persistence.firestore.data.Document.Document

case class FirestorePersistentRepr(persistenceId: String, sequence: Long, data: Document)
