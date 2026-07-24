package com.khaledhossameldin.telephony_sms

/**
 * The outcome of a single SMS send, once every part has reported.
 *
 * [errorCode] is null when the send succeeded, and otherwise carries the error
 * code that crosses the method channel.
 */
internal data class SendOutcome(
  val errorCode: String?,
  val message: String?,
) {
  val isSuccess: Boolean get() = errorCode == null

  internal companion object {
    val success = SendOutcome(errorCode = null, message = null)
  }
}

/**
 * Aggregates the per-part broadcasts of one SMS send into a single outcome.
 *
 * A long message is split into several parts and each part reports its own
 * result, out of order and possibly more than once. This class collects them and
 * decides when the send as a whole is finished.
 *
 * Deliberately free of Android types so it can be unit tested without an
 * emulator or Robolectric — the interesting logic lives here, and the plugin
 * keeps only the platform plumbing.
 */
internal class PendingSend(
  val id: String,
  val partCount: Int,
) {
  init {
    require(partCount > 0) { "A send must have at least one part." }
  }

  private val reported = mutableSetOf<Int>()

  /** The first failure seen, which is the one reported to the caller. */
  private var failure: SendOutcome? = null

  /** True once every part has reported at least once. */
  val isComplete: Boolean get() = reported.size >= partCount

  /**
   * Records the platform result code for one part.
   *
   * Repeat reports for the same part are ignored, so a duplicate broadcast
   * cannot complete the send early or overwrite an earlier failure. Returns true
   * when this call completed the send.
   */
  fun recordPart(index: Int, resultCode: Int): Boolean {
    if (index < 0 || index >= partCount) return isComplete
    if (!reported.add(index)) return isComplete

    if (resultCode != RESULT_OK && failure == null) {
      failure = SendOutcome(
        errorCode = errorCodeFor(resultCode),
        message = failureMessage(index, resultCode),
      )
    }
    return isComplete
  }

  /** The aggregate outcome. Only meaningful once [isComplete] is true. */
  val outcome: SendOutcome get() = failure ?: SendOutcome.success

  private fun failureMessage(index: Int, resultCode: Int): String {
    val reason = reasonFor(resultCode)
    return if (partCount == 1) {
      "The message could not be sent: $reason."
    } else {
      "Part ${index + 1} of $partCount could not be sent: $reason."
    }
  }

  internal companion object {
    /** `android.app.Activity.RESULT_OK`, inlined to keep this class Android-free. */
    const val RESULT_OK = -1

    /** `SmsManager.RESULT_ERROR_GENERIC_FAILURE` */
    const val RESULT_ERROR_GENERIC_FAILURE = 1

    /** `SmsManager.RESULT_ERROR_RADIO_OFF` */
    const val RESULT_ERROR_RADIO_OFF = 2

    /** `SmsManager.RESULT_ERROR_NULL_PDU` */
    const val RESULT_ERROR_NULL_PDU = 3

    /** `SmsManager.RESULT_ERROR_NO_SERVICE` */
    const val RESULT_ERROR_NO_SERVICE = 4

    fun errorCodeFor(resultCode: Int): String = when (resultCode) {
      RESULT_ERROR_GENERIC_FAILURE -> "generic_failure"
      RESULT_ERROR_RADIO_OFF -> "radio_off"
      RESULT_ERROR_NULL_PDU -> "null_pdu"
      RESULT_ERROR_NO_SERVICE -> "no_service"
      else -> "send_failed"
    }

    fun reasonFor(resultCode: Int): String = when (resultCode) {
      RESULT_ERROR_GENERIC_FAILURE -> "generic failure"
      RESULT_ERROR_RADIO_OFF -> "the radio is off"
      RESULT_ERROR_NULL_PDU -> "null PDU"
      RESULT_ERROR_NO_SERVICE -> "no service"
      else -> "unknown result code $resultCode"
    }
  }
}
