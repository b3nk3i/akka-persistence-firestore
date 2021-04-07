# Firestore Plugins for Akka Persistence

[![Scala CI](https://github.com/b3nk3i/akka-persistence-firestore/actions/workflows/scala.yml/badge.svg)](https://github.com/b3nk3i/akka-persistence-firestore/actions/workflows/scala.yml)

[Akka Persistence](https://doc.akka.io/docs/akka/current/scala/persistence.html) journal and query
plugin for [Firestore](https://cloud.google.com/firestore) (google document database).

Note that this project is in early access

## Project status

The plugins are released in 0.0.1 version.

## Usage

Enable the plugin in config:
```
akka.persistence {
  journal {
    plugin = "firestore-journal"
    auto-start-journals = ["firestore-journal"]
  }
}

firestore-journal {

  class = "org.benkei.akka.persistence.firestore.journal.FirestoreJournalPlugin"

  project-id = "<GOOGLE_PROJECT_ID>"

  root = "<COLLECTION_PREFIX>"
}

firestore-read-journal {

  project-id = "<GOOGLE_PROJECT_ID>"

  root = "<COLLECTION_PREFIX>"
}
```

## License

Akka Persistence Firestore is Open Source and available under the Apache 2 License.
