package com.khaledhossameldin.telephony_sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the multipart aggregation directly. This is the part of send reporting
 * with real logic in it — broadcasts arrive per part, out of order, and
 * sometimes twice — and it is deliberately free of Android types so it can be
 * tested without an emulator.
 */
internal class PendingSendTest {

  private companion object {
    const val OK = SendResultCodes.RESULT_OK
    const val NO_SERVICE = SendResultCodes.ERROR_NO_SERVICE
    const val RADIO_OFF = SendResultCodes.ERROR_RADIO_OFF
  }

  @Test
  fun singlePart_success_completesAndSucceeds() {
    val send = PendingSend(partCount = 1)

    assertFalse(send.isComplete)
    send.recordPart(0, OK)

    assertTrue(send.isComplete)
    assertFalse(send.hasFailed)
    assertTrue(send.outcome.isSuccess)
    assertNull(send.outcome.errorCode)
  }

  @Test
  fun singlePart_failure_reportsMappedCode() {
    val send = PendingSend(partCount = 1)

    send.recordPart(0, NO_SERVICE)

    assertTrue(send.hasFailed)
    assertFalse(send.outcome.isSuccess)
    assertEquals("no_service", send.outcome.errorCode)
    // A single-part message should not be described as "part 1 of 1".
    assertEquals(
      "The message could not be sent: there is no cellular service.",
      send.outcome.message,
    )
  }

  @Test
  fun multipart_completesOnlyAfterEveryPartReports() {
    val send = PendingSend(partCount = 3)

    send.recordPart(0, OK)
    assertFalse(send.isComplete)
    send.recordPart(1, OK)
    assertFalse(send.isComplete)

    send.recordPart(2, OK)
    assertTrue(send.isComplete)
    assertTrue(send.outcome.isSuccess)
  }

  @Test
  fun multipart_outOfOrderReports_stillComplete() {
    val send = PendingSend(partCount = 3)

    send.recordPart(2, OK)
    send.recordPart(0, OK)
    assertFalse(send.isComplete)
    send.recordPart(1, OK)

    assertTrue(send.isComplete)
    assertTrue(send.outcome.isSuccess)
  }

  /**
   * The reason [PendingSend.hasFailed] exists. When a part fails the radio
   * usually abandons the rest, so their broadcasts never arrive — waiting for
   * [PendingSend.isComplete] would trade the real cause for a timeout.
   */
  @Test
  fun multipart_failureIsVisibleBeforeTheSendCompletes() {
    val send = PendingSend(partCount = 3)

    send.recordPart(0, RADIO_OFF)

    assertTrue(send.hasFailed)
    assertFalse(send.isComplete)
    assertEquals("radio_off", send.outcome.errorCode)
    assertEquals("Part 1 of 3 could not be sent: the radio is off.", send.outcome.message)
  }

  @Test
  fun multipart_oneFailingPart_failsWholeSendAndNamesThePart() {
    val send = PendingSend(partCount = 3)

    send.recordPart(0, OK)
    send.recordPart(1, NO_SERVICE)
    send.recordPart(2, OK)

    assertTrue(send.isComplete)
    assertFalse(send.outcome.isSuccess)
    assertEquals("no_service", send.outcome.errorCode)
    assertEquals(
      "Part 2 of 3 could not be sent: there is no cellular service.",
      send.outcome.message,
    )
  }

  @Test
  fun multipart_firstFailureWins() {
    val send = PendingSend(partCount = 3)

    send.recordPart(0, RADIO_OFF)
    send.recordPart(1, NO_SERVICE)
    send.recordPart(2, OK)

    assertEquals("radio_off", send.outcome.errorCode)
  }

  /**
   * A duplicate broadcast must not count twice, or a two-part send would look
   * complete after one part reported twice.
   */
  @Test
  fun duplicatePartReports_areIgnored() {
    val send = PendingSend(partCount = 2)

    send.recordPart(0, OK)
    send.recordPart(0, OK)
    assertFalse(send.isComplete)

    send.recordPart(1, OK)
    assertTrue(send.isComplete)
  }

  /** A duplicate must not be able to turn a recorded failure into a success. */
  @Test
  fun duplicatePartReport_cannotOverwriteFailure() {
    val send = PendingSend(partCount = 1)

    send.recordPart(0, NO_SERVICE)
    send.recordPart(0, OK)

    assertTrue(send.hasFailed)
    assertEquals("no_service", send.outcome.errorCode)
  }

  @Test
  fun outOfRangePartIndex_isIgnored() {
    val send = PendingSend(partCount = 2)

    send.recordPart(-1, OK)
    send.recordPart(7, OK)

    assertFalse(send.isComplete)
    assertFalse(send.hasFailed)
  }

  /** An out-of-range index must not be able to fail an otherwise fine send. */
  @Test
  fun outOfRangeFailure_isIgnored() {
    val send = PendingSend(partCount = 2)

    send.recordPart(-1, NO_SERVICE)

    assertFalse(send.hasFailed)
  }

  @Test
  fun unknownResultCode_namesTheNumber() {
    val send = PendingSend(partCount = 1)

    send.recordPart(0, 9_999)

    assertEquals("generic_failure", send.outcome.errorCode)
    assertTrue(send.outcome.message!!.contains("9999"))
  }

  // The delivery half of a send records states read out of a status report PDU
  // rather than result codes, through the same aggregation.

  @Test
  fun delivery_succeedsOnlyWhenEveryPartIsDelivered() {
    val delivery = PendingSend(partCount = 2)

    delivery.recordSuccess(0)
    assertFalse(delivery.isComplete)

    delivery.recordSuccess(1)
    assertTrue(delivery.isComplete)
    assertTrue(delivery.outcome.isSuccess)
  }

  @Test
  fun delivery_failureIsReportedWithItsOwnWording() {
    val delivery = PendingSend(partCount = 2)

    delivery.recordFailure(1, "not_delivered", "${delivery.describePart(1)} was not delivered.")

    assertTrue(delivery.hasFailed)
    assertEquals("not_delivered", delivery.outcome.errorCode)
    assertEquals("Part 2 of 2 was not delivered.", delivery.outcome.message)
  }

  @Test
  fun describePart_namesASinglePartMessageWithoutAnIndex() {
    assertEquals("The message", PendingSend(partCount = 1).describePart(0))
    assertEquals("Part 3 of 4", PendingSend(partCount = 4).describePart(2))
  }
}
