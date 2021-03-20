package org.benkei.akka.persistence.firestore

case class FirestorePersistentRepr(persistenceId: String, sequence: Long, data: Map[String, Any])
