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
 * decides when the send as a whole is finished. It backs both halves of a send:
 * the sent broadcasts, which carry a platform result code, and the delivery
 * reports, whose state has to be read out of a PDU first.
 *
 * Deliberately free of Android types so it can be unit tested without an
 * emulator or Robolectric — the interesting logic lives here, and the plugin
 * keeps only the platform plumbing.
 */
internal class PendingSend(val partCount: Int) {
  init {
    require(partCount > 0) { "A send must have at least one part." }
  }

  private val reported = mutableSetOf<Int>()

  /** The first failure seen, which is the one reported to the caller. */
  private var failure: SendOutcome? = null

  /** True once every part has reported at least once. */
  val isComplete: Boolean get() = reported.size >= partCount

  /**
   * True once any part has failed.
   *
   * Callers should report as soon as this flips rather than waiting for
   * [isComplete]. When one part fails the radio usually abandons the rest, so
   * their broadcasts never arrive and waiting only trades the real cause for a
   * timeout. Nothing a later part can report would change the answer either,
   * since the first failure is the one kept.
   */
  val hasFailed: Boolean get() = failure != null

  /** The aggregate outcome. Meaningful once [hasFailed] or [isComplete]. */
  val outcome: SendOutcome get() = failure ?: SendOutcome.success

  /**
   * Records a platform result code for one part of a sent broadcast.
   *
   * Repeat reports for the same part are ignored, so a duplicate broadcast
   * cannot complete the send early or overwrite an earlier failure.
   */
  fun recordPart(index: Int, resultCode: Int) {
    if (resultCode == SendResultCodes.RESULT_OK) {
      recordSuccess(index)
      return
    }

    val platformFailure = SendResultCodes.failureFor(resultCode)
    recordFailure(
      index,
      platformFailure.code,
      "${describePart(index)} could not be sent: ${platformFailure.reason}.",
    )
  }

  /** Records one part as having succeeded. */
  fun recordSuccess(index: Int) {
    claim(index)
  }

  /** Records one part as having failed, with the [message] shown to the caller. */
  fun recordFailure(index: Int, errorCode: String, message: String) {
    if (!claim(index)) return
    if (failure == null) failure = SendOutcome(errorCode, message)
  }

  /**
   * How to refer to one part in a message to the caller. A single-part message
   * should not be described as "part 1 of 1".
   */
  fun describePart(index: Int): String =
    if (partCount == 1) "The message" else "Part ${index + 1} of $partCount"

  /** Takes the slot for [index], returning false if it is out of range or taken. */
  private fun claim(index: Int): Boolean {
    if (index < 0 || index >= partCount) return false
    return reported.add(index)
  }
}
