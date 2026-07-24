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
    const val OK = PendingSend.RESULT_OK
    const val NO_SERVICE = PendingSend.RESULT_ERROR_NO_SERVICE
    const val RADIO_OFF = PendingSend.RESULT_ERROR_RADIO_OFF
  }

  @Test
  fun singlePart_success_completesAndSucceeds() {
    val send = PendingSend("id", partCount = 1)

    assertFalse(send.isComplete)
    assertTrue(send.recordPart(0, OK))
    assertTrue(send.isComplete)
    assertTrue(send.outcome.isSuccess)
    assertNull(send.outcome.errorCode)
  }

  @Test
  fun singlePart_failure_reportsMappedCode() {
    val send = PendingSend("id", partCount = 1)

    send.recordPart(0, NO_SERVICE)

    assertTrue(send.isComplete)
    assertFalse(send.outcome.isSuccess)
    assertEquals("no_service", send.outcome.errorCode)
    // A single-part message should not be described as "part 1 of 1".
    assertEquals("The message could not be sent: no service.", send.outcome.message)
  }

  @Test
  fun multipart_completesOnlyAfterEveryPartReports() {
    val send = PendingSend("id", partCount = 3)

    assertFalse(send.recordPart(0, OK))
    assertFalse(send.isComplete)
    assertFalse(send.recordPart(1, OK))
    assertFalse(send.isComplete)

    assertTrue(send.recordPart(2, OK))
    assertTrue(send.isComplete)
    assertTrue(send.outcome.isSuccess)
  }

  @Test
  fun multipart_outOfOrderReports_stillComplete() {
    val send = PendingSend("id", partCount = 3)

    send.recordPart(2, OK)
    send.recordPart(0, OK)
    assertFalse(send.isComplete)
    send.recordPart(1, OK)

    assertTrue(send.isComplete)
    assertTrue(send.outcome.isSuccess)
  }

  @Test
  fun multipart_oneFailingPart_failsWholeSendAndNamesThePart() {
    val send = PendingSend("id", partCount = 3)

    send.recordPart(0, OK)
    send.recordPart(1, NO_SERVICE)
    send.recordPart(2, OK)

    assertTrue(send.isComplete)
    assertFalse(send.outcome.isSuccess)
    assertEquals("no_service", send.outcome.errorCode)
    assertEquals("Part 2 of 3 could not be sent: no service.", send.outcome.message)
  }

  @Test
  fun multipart_firstFailureWins() {
    val send = PendingSend("id", partCount = 3)

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
    val send = PendingSend("id", partCount = 2)

    assertFalse(send.recordPart(0, OK))
    assertFalse(send.recordPart(0, OK))
    assertFalse(send.isComplete)

    assertTrue(send.recordPart(1, OK))
    assertTrue(send.isComplete)
  }

  /** A duplicate must not be able to turn a recorded failure into a success. */
  @Test
  fun duplicatePartReport_cannotOverwriteFailure() {
    val send = PendingSend("id", partCount = 1)

    send.recordPart(0, NO_SERVICE)
    send.recordPart(0, OK)

    assertFalse(send.outcome.isSuccess)
    assertEquals("no_service", send.outcome.errorCode)
  }

  @Test
  fun outOfRangePartIndex_isIgnored() {
    val send = PendingSend("id", partCount = 2)

    assertFalse(send.recordPart(-1, OK))
    assertFalse(send.recordPart(7, OK))
    assertFalse(send.isComplete)
  }

  @Test
  fun unknownResultCode_fallsBackToSendFailed() {
    val send = PendingSend("id", partCount = 1)

    send.recordPart(0, 99)

    assertEquals("send_failed", send.outcome.errorCode)
    assertTrue(send.outcome.message!!.contains("99"))
  }

  @Test
  fun errorCodeMapping_coversEveryPlatformCode() {
    assertEquals(
      "generic_failure",
      PendingSend.errorCodeFor(PendingSend.RESULT_ERROR_GENERIC_FAILURE),
    )
    assertEquals("radio_off", PendingSend.errorCodeFor(PendingSend.RESULT_ERROR_RADIO_OFF))
    assertEquals("null_pdu", PendingSend.errorCodeFor(PendingSend.RESULT_ERROR_NULL_PDU))
    assertEquals("no_service", PendingSend.errorCodeFor(PendingSend.RESULT_ERROR_NO_SERVICE))
  }
}
