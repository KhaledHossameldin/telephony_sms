package com.khaledhossameldin.telephony_sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the result-code table.
 *
 * Only four of the platform's codes used to be mapped, so everything else — the
 * send rate limit above all — surfaced as an opaque "unknown result code". These
 * cases cover one member of every group, plus the ones users actually hit.
 */
internal class SendResultCodesTest {

  private fun codeFor(resultCode: Int) = SendResultCodes.failureFor(resultCode).code

  @Test
  fun theFourOriginalCodes_areUnchanged() {
    assertEquals("generic_failure", codeFor(SendResultCodes.ERROR_GENERIC_FAILURE))
    assertEquals("radio_off", codeFor(SendResultCodes.ERROR_RADIO_OFF))
    assertEquals("null_pdu", codeFor(SendResultCodes.ERROR_NULL_PDU))
    assertEquals("no_service", codeFor(SendResultCodes.ERROR_NO_SERVICE))
  }

  /**
   * Android throttles how many messages an app may send in a window, so this is
   * the usual failure when sending in a loop — and the single most valuable code
   * to name rather than swallow.
   */
  @Test
  fun rateLimit_isNamed() {
    assertEquals("limit_exceeded", codeFor(SendResultCodes.ERROR_LIMIT_EXCEEDED))
    assertEquals("limit_exceeded", codeFor(SendResultCodes.RIL_REQUEST_RATE_LIMITED))
  }

  @Test
  fun shortCodes_keepTheirDistinctCodes() {
    assertEquals("short_code_not_allowed", codeFor(SendResultCodes.ERROR_SHORT_CODE_NOT_ALLOWED))
    assertEquals(
      "short_code_never_allowed",
      codeFor(SendResultCodes.ERROR_SHORT_CODE_NEVER_ALLOWED),
    )
  }

  @Test
  fun simProblems_areDistinguishedFromEachOther() {
    assertEquals("sim_absent", codeFor(SendResultCodes.RIL_SIM_ABSENT))
    assertEquals("sim_absent", codeFor(SendResultCodes.RIL_NO_SUBSCRIPTION))
    assertEquals("sim_error", codeFor(SendResultCodes.RIL_SIM_BUSY))
    assertEquals("sim_error", codeFor(SendResultCodes.RIL_SIM_FULL))
    assertEquals("sim_error", codeFor(SendResultCodes.RIL_INVALID_SIM_STATE))
  }

  @Test
  fun appAndUserLevelRefusals_areNamed() {
    assertEquals("no_default_sms_app", codeFor(SendResultCodes.NO_DEFAULT_SMS_APP))
    assertEquals("user_not_allowed", codeFor(SendResultCodes.USER_NOT_ALLOWED))
    assertEquals("fdn_check_failure", codeFor(SendResultCodes.ERROR_FDN_CHECK_FAILURE))
    assertEquals(
      "blocked_during_emergency",
      codeFor(SendResultCodes.SMS_BLOCKED_DURING_EMERGENCY),
    )
  }

  @Test
  fun radioLayerCodes_foldOntoTheirFrameworkEquivalents() {
    assertEquals("radio_not_available", codeFor(SendResultCodes.RADIO_NOT_AVAILABLE))
    assertEquals("radio_not_available", codeFor(SendResultCodes.RIL_RADIO_NOT_AVAILABLE))
    assertEquals("network_reject", codeFor(SendResultCodes.NETWORK_REJECT))
    assertEquals("network_reject", codeFor(SendResultCodes.RIL_NETWORK_REJECT))
    assertEquals("modem_error", codeFor(SendResultCodes.MODEM_ERROR))
    assertEquals("modem_error", codeFor(SendResultCodes.RIL_MODEM_ERR))
    assertEquals("no_memory", codeFor(SendResultCodes.NO_MEMORY))
    assertEquals("no_memory", codeFor(SendResultCodes.RIL_NO_MEMORY))
  }

  @Test
  fun retryExhaustion_isANetworkError() {
    assertEquals("network_error", codeFor(SendResultCodes.SMS_SEND_FAILED_AFTER_MAX_RETRY))
    assertEquals("network_error", codeFor(SendResultCodes.RIL_SMS_SEND_FAIL_RETRY))
  }

  /**
   * Every constant the platform defines is named explicitly. The fallback
   * reason quotes the number, so its absence proves the table has no holes.
   */
  @Test
  fun noPlatformCodeFallsThroughToTheFallback() {
    for (resultCode in (0..33) + (100..138)) {
      val failure = SendResultCodes.failureFor(resultCode)

      assertTrue(failure.code.isNotBlank(), "result code $resultCode has no error code")
      assertFalse(
        failure.reason.contains("result code"),
        "result code $resultCode is not in the table",
      )
    }
  }

  /**
   * A code from a platform newer than the table. It must still name the number
   * so it can be looked up.
   */
  @Test
  fun unknownCode_fallsBackAndReportsTheNumber() {
    val failure = SendResultCodes.failureFor(9_999)

    assertEquals("generic_failure", failure.code)
    assertTrue(failure.reason.contains("9999"))
  }
}
