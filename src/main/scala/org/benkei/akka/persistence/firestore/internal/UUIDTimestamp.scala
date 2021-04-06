package org.benkei.akka.persistence.firestore.internal

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.temporal.TemporalAccessor

import akka.annotation.InternalApi

/**
  * INTERNAL API
  */
@InternalApi
private[akka] object UUIDTimestamp {
  val GMT = ZoneId.of("GMT")

  // UUID v1 timestamp must be in 100-nanoseconds interval since Oct 15 1582 00:00:00.000
  // this is the offset of that from the unix timestamp starting point Jan 01 1970 00:00:00.000
  // in ms
  private val StartEpochMillis = -12219292800000L
  private val UUIDUnitsPerMs   = 10000

  val MinVal = UUIDTimestamp(0L) // as the long is signed
  val MaxVal = UUIDTimestamp(Long.MaxValue) // as the long is signed

  def now(): UUIDTimestamp = fromUnixTimestamp(System.currentTimeMillis())

  def fromUnixTimestamp(unixTimestampMs: Long): UUIDTimestamp =
    UUIDTimestamp((unixTimestampMs - StartEpochMillis) * UUIDUnitsPerMs)

  def apply(accessor: TemporalAccessor): UUIDTimestamp = {
    val instant         = Instant.from(accessor)
    val unixTimestampMs = instant.toEpochMilli
    // nano is from the last sec, not milli
    // and then we want 100-nanosecond interval
    val extraNanos = instant.getNano % 1000000
    UUIDTimestamp((unixTimestampMs - StartEpochMillis) * UUIDUnitsPerMs + extraNanos) //+ (instant.getNano * 100))
  }
}

/**
  * INTERNAL API
  *
 * UUID v1 timestamp in 100-nanoseconds interval since Oct 15 1582 00:00:00.000
  */
@InternalApi
private[akka] final case class UUIDTimestamp(nanoTimestamp: Long) extends AnyVal with Ordered[UUIDTimestamp] {
  import UUIDTimestamp._

  /**
    * @return The UUIDv1 timestamp truncated to millis
    */
  def toMs: Long = nanoTimestamp / UUIDUnitsPerMs
  def toInstant: ZonedDateTime = {
    val epochMillis   = toMs + UUIDTimestamp.StartEpochMillis
    val leftOverNanos = nanoTimestamp % UUIDUnitsPerMs
    Instant
      .ofEpochMilli(epochMillis)
      .plusNanos(leftOverNanos)
      .atZone(UUIDTimestamp.GMT)
  }

  /**
    * Note that this conversion is lossy
    * since the UUID timestamp is in 100s of nanos.
    *  @return The timestamp as a unix timestamp in milliseconds
    */
  def toUnixTimestamp: Long =
    (nanoTimestamp / UUIDUnitsPerMs) + StartEpochMillis

  def compare(that: UUIDTimestamp): Int = this.nanoTimestamp.compare(that.nanoTimestamp)

  def next: UUIDTimestamp = UUIDTimestamp(nanoTimestamp + 1)
}
