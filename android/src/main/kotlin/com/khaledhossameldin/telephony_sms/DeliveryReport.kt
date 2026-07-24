package com.khaledhossameldin.telephony_sms

/** What a carrier's status report says about one message part. */
internal enum class DeliveryState {
  /** The carrier confirmed the message reached the handset. */
  DELIVERED,

  /** Accepted, or a temporary failure the carrier is still retrying. */
  PENDING,

  /** The carrier gave up. */
  FAILED,
}

/**
 * Reads a delivery outcome out of the status field of an SMS-STATUS-REPORT.
 *
 * The delivery [android.app.PendingIntent] is broadcast with a result code of
 * `RESULT_OK` whether the report says delivered or permanently failed — the real
 * answer is only in the report's PDU, which `SmsManager` documents as arriving in
 * the "pdu" extra. Reading the result code instead reports every failed delivery
 * as a success.
 *
 * [status] is `SmsMessage.getStatus()`:
 *
 * * **GSM** — the TP-Status octet, TS 23.040 §9.2.3.15. `0x00..0x1F` completed,
 *   `0x20..0x3F` temporary error with the service centre still trying,
 *   `0x40..0x7F` the service centre has stopped trying.
 * * **CDMA** — shifted left by 16, with an error class in bits 25-24 and a status
 *   code in bits 23-16, C.S0015-B §4.5.21. Error class `0` is no error, `2` is
 *   temporary, `3` is permanent. Only status code `2` within error class `0`
 *   means the message actually reached the handset; `0` and `1` are "accepted"
 *   and "deposited", which is not the same thing.
 *
 * Android-free on purpose: PDU parsing needs the framework, but deciding what a
 * status means does not, and this is where the logic worth testing lives.
 */
internal fun classifyDeliveryStatus(status: Int): DeliveryState {
  // A GSM TP-Status is a single octet, so anything in the upper half is CDMA.
  if (status ushr 16 != 0) return classifyCdmaStatus(status)

  return when {
    status <= GSM_COMPLETED_MAX -> DeliveryState.DELIVERED
    status <= GSM_TEMPORARY_MAX -> DeliveryState.PENDING
    else -> DeliveryState.FAILED
  }
}

private fun classifyCdmaStatus(status: Int): DeliveryState {
  val errorClass = (status ushr 24) and 0b11
  val statusCode = (status ushr 16) and 0xFF

  return when (errorClass) {
    CDMA_ERROR_CLASS_NONE -> when (statusCode) {
      CDMA_STATUS_DELIVERED -> DeliveryState.DELIVERED
      CDMA_STATUS_CANCELLED -> DeliveryState.FAILED
      // Accepted or deposited: on its way, but not yet at the handset.
      else -> DeliveryState.PENDING
    }

    CDMA_ERROR_CLASS_TEMPORARY -> DeliveryState.PENDING

    else -> DeliveryState.FAILED
  }
}

/** Highest TP-Status meaning the transaction completed. */
internal const val GSM_COMPLETED_MAX: Int = 0x1F

/** Highest TP-Status meaning a temporary error the service centre is retrying. */
internal const val GSM_TEMPORARY_MAX: Int = 0x3F

internal const val CDMA_ERROR_CLASS_NONE: Int = 0
internal const val CDMA_ERROR_CLASS_TEMPORARY: Int = 2

internal const val CDMA_STATUS_DELIVERED: Int = 2
internal const val CDMA_STATUS_CANCELLED: Int = 3
