package org.benkei.akka.persistence.firestore.journal

case class FirestorePersistentRepr(persistenceId: String, sequence: Long, data: Map[String, Any])
