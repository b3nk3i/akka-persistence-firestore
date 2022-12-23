package org.b3nk3i.akka.persistence.firestore.snapshot

import akka.stream.Materializer
import cats.implicits.toTraverseOps
import com.google.cloud.firestore.{Firestore, Query}
import org.b3nk3i.akka.persistence.firestore.data.Field
import org.b3nk3i.akka.persistence.firestore.internal.UUIDGenerator
import org.b3nk3i.akka.persistence.firestore.journal.FireStoreDao.asFirestoreRepr
import org.b3nk3i.akka.persistence.firestore.journal.FirestorePersistentRepr
import org.b3nk3i.akka.persistence.firestore.snapshot.FireStoreSnapshotDao.SnapshotStore
import org.b3nk3i.google.ApiFuturesOps.ApiFutureExt

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters._

class FireStoreSnapshotDao(db: Firestore, rootCollection: String, enqueueTimeout: Duration)(implicit
  ec:                          ExecutionContextExecutor,
  mat:                         Materializer
) {

  private val uuidGenerator = UUIDGenerator()

  def latestSnapshot(persistenceId: String): Future[Option[FirestorePersistentRepr]] = {
    db.collection(rootCollection)
      .document(persistenceId)
      .collection(SnapshotStore)
      .orderBy(Field.Sequence.name, Query.Direction.DESCENDING)
      .limit(1)
      .get()
      .futureLift
      .map(_.getDocuments.asScala.toList.headOption)
      .flatMap(maybeSnapshot => maybeSnapshot.traverse(asFirestoreRepr(persistenceId, _)))
  }
}

object FireStoreSnapshotDao {

  val SnapshotStore = "snapshot-store"

}
