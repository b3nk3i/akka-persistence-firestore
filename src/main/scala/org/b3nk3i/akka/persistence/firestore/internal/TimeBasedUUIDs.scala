package org.b3nk3i.akka.persistence.firestore.internal

import java.time.Instant
import java.time.format.{DateTimeFormatterBuilder, SignStyle}
import java.time.temporal.ChronoField
import java.util.{Comparator, UUID}

import akka.annotation.InternalApi

/**
  * Same as akka-persistence-couchbase TimeBasedUUIDs,
  *  fall back added if MAC not found as not always accessible in the Cloud
  */
@InternalApi private[akka] object TimeBasedUUIDs {
  val MinLSB = 0x0000000000000000L
  val MaxLSB = 0x7f7f7f7f7f7f7f7fL

  val MinUUID = create(UUIDTimestamp.MinVal, MinLSB)
  val MaxUUID = create(UUIDTimestamp.MaxVal, MaxLSB)

  def lsbFromNode(node: Long, clock: Int): Long = {
    var lsb = 0L
    lsb |= (clock & 0x0000000000003fffL) << 48
    lsb |= 0x8000000000000000L
    lsb |= node
    lsb
  }

  def msbFromTimestamp(timestamp: UUIDTimestamp): Long = {
    var msb = 0L
    msb |= (0x00000000ffffffffL & timestamp.nanoTimestamp) << 32
    msb |= (0x0000ffff00000000L & timestamp.nanoTimestamp) >>> 16
    msb |= (0x0fff000000000000L & timestamp.nanoTimestamp) >>> 48
    msb |= 0x0000000000001000L // sets the version to 1.
    msb
  }

  def create(timestamp: UUIDTimestamp, lsb: Long): UUID =
    new UUID(msbFromTimestamp(timestamp), lsb)
}

/**
  * INTERNAL API
  *
 * Comparator that sorts the same as the string format in TimeBasedUUIDSerialization
  */
@InternalApi private[akka] final class TimeBasedUUIDComparator extends Comparator[UUID] {
  import java.lang.Long.compareUnsigned

  def compare(u1: UUID, u2: UUID): Int = {
    require(u1.version() == 1)
    require(u2.version() == 1)

    // order by time stamp:
    val diff2 = u1.timestamp().compareTo(u2.timestamp())
    if (diff2 != 0) diff2
    else
      // or if that won't work, by other bits lexically
      compareUnsigned(u1.getLeastSignificantBits, u2.getLeastSignificantBits)
  }
}

/**
  * INTERNAL API
  */
@InternalApi private[akka] object TimeBasedUUIDComparator {
  val comparator: Comparator[UUID] = new TimeBasedUUIDComparator
}

/**
  * INTERNAL API
  */
@InternalApi private[akka] object TimeBasedUUIDSerialization {
  // very close to ISO 8660 since that is string-sortable, but including nanos
  private val SortableTimeFormatter = new DateTimeFormatterBuilder()
    .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
    .appendLiteral('-')
    .appendValue(ChronoField.MONTH_OF_YEAR, 2)
    .appendLiteral('-')
    .appendValue(ChronoField.DAY_OF_MONTH, 2)
    .appendLiteral('T')
    .appendValue(ChronoField.HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
    .appendLiteral(':')
    .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
    .appendLiteral('.')
    // include nano of second instead of standard millis to not lose precision
    .appendValue(ChronoField.NANO_OF_SECOND, 9)
    .toFormatter()
    .withZone(UUIDTimestamp.GMT)

  /**
    * Generate a string that sorts the same as the TimeBasedUUIDComparator
    */
  def toSortableString(id: UUID): String = {
    require(id.version() == 1)
    val builder = new StringBuilder()
    val instant = UUIDTimestamp(id.timestamp()).toInstant
    builder.append(SortableTimeFormatter.format(instant))
    builder.append('_')
    builder.append("%20s".format(java.lang.Long.toUnsignedString(id.getLeastSignificantBits)))
    builder.toString()
  }

  def fromSortableString(text: String): UUID = {
    val parts     = text.split('_')
    val parsed    = SortableTimeFormatter.parse(parts(0))
    val instant   = Instant.from(parsed).atZone(UUIDTimestamp.GMT)
    val timestamp = UUIDTimestamp(instant)
    val lsb       = java.lang.Long.parseUnsignedLong(parts(1).trim)
    TimeBasedUUIDs.create(timestamp, lsb)
  }
}
