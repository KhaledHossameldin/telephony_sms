package com.khaledhossameldin.telephony_sms

/** A platform send failure, translated into what crosses the method channel. */
internal data class SendFailure(
  val code: String,
  /** Reads as the tail of "The message could not be sent: $reason." */
  val reason: String,
)

/**
 * Translates `android.telephony.SmsManager` result codes into plugin error codes.
 *
 * The platform defines around eighty of these and the sent broadcast can carry
 * any of them. Mapping only the four oldest leaves the common ones — the send
 * rate limit above all — surfacing as an opaque "unknown result code".
 *
 * The values are inlined rather than referenced from [android.telephony.SmsManager]
 * so this file stays free of Android types and testable on a plain JVM. They are
 * frozen platform constants; the source of truth is `android.jar` for the
 * `compileSdk` this plugin builds against.
 *
 * Codes are grouped by what a caller can *do* about them rather than kept
 * one-to-one, so the Dart enum stays small enough to switch over. Several
 * platform codes therefore share one plugin code.
 */
internal object SendResultCodes {

  /** `android.app.Activity.RESULT_OK`, the only value that means success. */
  const val RESULT_OK: Int = -1

  // Codes reported directly by the telephony framework.

  const val ERROR_NONE: Int = 0
  const val ERROR_GENERIC_FAILURE: Int = 1
  const val ERROR_RADIO_OFF: Int = 2
  const val ERROR_NULL_PDU: Int = 3
  const val ERROR_NO_SERVICE: Int = 4
  const val ERROR_LIMIT_EXCEEDED: Int = 5
  const val ERROR_FDN_CHECK_FAILURE: Int = 6
  const val ERROR_SHORT_CODE_NOT_ALLOWED: Int = 7
  const val ERROR_SHORT_CODE_NEVER_ALLOWED: Int = 8
  const val RADIO_NOT_AVAILABLE: Int = 9
  const val NETWORK_REJECT: Int = 10
  const val INVALID_ARGUMENTS: Int = 11
  const val INVALID_STATE: Int = 12
  const val NO_MEMORY: Int = 13
  const val INVALID_SMS_FORMAT: Int = 14
  const val SYSTEM_ERROR: Int = 15
  const val MODEM_ERROR: Int = 16
  const val NETWORK_ERROR: Int = 17
  const val ENCODING_ERROR: Int = 18
  const val INVALID_SMSC_ADDRESS: Int = 19
  const val OPERATION_NOT_ALLOWED: Int = 20
  const val INTERNAL_ERROR: Int = 21
  const val NO_RESOURCES: Int = 22
  const val CANCELLED: Int = 23
  const val REQUEST_NOT_SUPPORTED: Int = 24
  const val NO_BLUETOOTH_SERVICE: Int = 25
  const val INVALID_BLUETOOTH_ADDRESS: Int = 26
  const val BLUETOOTH_DISCONNECTED: Int = 27
  const val UNEXPECTED_EVENT_STOP_SENDING: Int = 28
  const val SMS_BLOCKED_DURING_EMERGENCY: Int = 29
  const val SMS_SEND_RETRY_FAILED: Int = 30
  const val REMOTE_EXCEPTION: Int = 31
  const val NO_DEFAULT_SMS_APP: Int = 32
  const val USER_NOT_ALLOWED: Int = 33

  // Codes forwarded from the radio interface layer, numbered from 100.

  const val RIL_RADIO_NOT_AVAILABLE: Int = 100
  const val RIL_SMS_SEND_FAIL_RETRY: Int = 101
  const val RIL_NETWORK_REJECT: Int = 102
  const val RIL_INVALID_STATE: Int = 103
  const val RIL_INVALID_ARGUMENTS: Int = 104
  const val RIL_NO_MEMORY: Int = 105
  const val RIL_REQUEST_RATE_LIMITED: Int = 106
  const val RIL_INVALID_SMS_FORMAT: Int = 107
  const val RIL_SYSTEM_ERR: Int = 108
  const val RIL_ENCODING_ERR: Int = 109
  const val RIL_INVALID_SMSC_ADDRESS: Int = 110
  const val RIL_MODEM_ERR: Int = 111
  const val RIL_NETWORK_ERR: Int = 112
  const val RIL_INTERNAL_ERR: Int = 113
  const val RIL_REQUEST_NOT_SUPPORTED: Int = 114
  const val RIL_INVALID_MODEM_STATE: Int = 115
  const val RIL_NETWORK_NOT_READY: Int = 116
  const val RIL_OPERATION_NOT_ALLOWED: Int = 117
  const val RIL_NO_RESOURCES: Int = 118
  const val RIL_CANCELLED: Int = 119
  const val RIL_SIM_ABSENT: Int = 120
  const val RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED: Int = 121
  const val RIL_ACCESS_BARRED: Int = 122
  const val RIL_BLOCKED_DUE_TO_CALL: Int = 123
  const val RIL_GENERIC_ERROR: Int = 124
  const val RIL_INVALID_RESPONSE: Int = 125
  const val RIL_SIM_PIN2: Int = 126
  const val RIL_SIM_PUK2: Int = 127
  const val RIL_SUBSCRIPTION_NOT_AVAILABLE: Int = 128
  const val RIL_SIM_ERROR: Int = 129
  const val RIL_INVALID_SIM_STATE: Int = 130
  const val RIL_NO_SMS_TO_ACK: Int = 131
  const val RIL_SIM_BUSY: Int = 132
  const val RIL_SIM_FULL: Int = 133
  const val RIL_NO_SUBSCRIPTION: Int = 134
  const val RIL_NO_NETWORK_FOUND: Int = 135
  const val RIL_DEVICE_IN_USE: Int = 136
  const val RIL_ABORTED: Int = 137
  const val SMS_SEND_FAILED_AFTER_MAX_RETRY: Int = 138

  /**
   * The code and reason for [resultCode].
   *
   * Returning both together keeps them from drifting apart, which two parallel
   * `when` expressions invite.
   */
  fun failureFor(resultCode: Int): SendFailure = when (resultCode) {
    ERROR_RADIO_OFF -> SendFailure("radio_off", "the radio is off")

    ERROR_NULL_PDU -> SendFailure("null_pdu", "the telephony stack produced a null PDU")

    ERROR_NO_SERVICE, RIL_NO_NETWORK_FOUND ->
      SendFailure("no_service", "there is no cellular service")

    ERROR_LIMIT_EXCEEDED, RIL_REQUEST_RATE_LIMITED ->
      SendFailure("limit_exceeded", "the device's SMS rate limit was reached")

    ERROR_FDN_CHECK_FAILURE ->
      SendFailure("fdn_check_failure", "the number is not in the SIM's fixed dialling list")

    ERROR_SHORT_CODE_NOT_ALLOWED ->
      SendFailure("short_code_not_allowed", "sending to this short code was not permitted")

    ERROR_SHORT_CODE_NEVER_ALLOWED ->
      SendFailure("short_code_never_allowed", "sending to this short code is never permitted")

    RADIO_NOT_AVAILABLE, RIL_RADIO_NOT_AVAILABLE ->
      SendFailure("radio_not_available", "the radio is unavailable")

    NETWORK_REJECT, RIL_NETWORK_REJECT, RIL_ACCESS_BARRED ->
      SendFailure("network_reject", "the network rejected the message")

    INVALID_ARGUMENTS, RIL_INVALID_ARGUMENTS ->
      SendFailure("invalid_arguments", "the telephony stack rejected the arguments")

    INVALID_STATE, SYSTEM_ERROR, INTERNAL_ERROR, REMOTE_EXCEPTION, RIL_INVALID_STATE,
    RIL_SYSTEM_ERR, RIL_INTERNAL_ERR, RIL_INVALID_RESPONSE, RIL_NO_SMS_TO_ACK,
    ->
      SendFailure("system_error", "the telephony system reported an internal error")

    NO_MEMORY, RIL_NO_MEMORY -> SendFailure("no_memory", "the device is out of memory")

    INVALID_SMS_FORMAT, RIL_INVALID_SMS_FORMAT ->
      SendFailure("invalid_sms_format", "the message format is invalid")

    MODEM_ERROR, RIL_MODEM_ERR, RIL_INVALID_MODEM_STATE ->
      SendFailure("modem_error", "the modem reported an error")

    NETWORK_ERROR, SMS_SEND_RETRY_FAILED, RIL_SMS_SEND_FAIL_RETRY, RIL_NETWORK_ERR,
    RIL_NETWORK_NOT_READY, RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED, RIL_BLOCKED_DUE_TO_CALL,
    RIL_DEVICE_IN_USE, SMS_SEND_FAILED_AFTER_MAX_RETRY,
    ->
      SendFailure("network_error", "the network could not carry the message")

    ENCODING_ERROR, RIL_ENCODING_ERR ->
      SendFailure("encoding_error", "the message could not be encoded")

    INVALID_SMSC_ADDRESS, RIL_INVALID_SMSC_ADDRESS ->
      SendFailure("invalid_smsc_address", "the SMS centre address is invalid")

    OPERATION_NOT_ALLOWED, REQUEST_NOT_SUPPORTED, RIL_REQUEST_NOT_SUPPORTED,
    RIL_OPERATION_NOT_ALLOWED,
    ->
      SendFailure("operation_not_allowed", "the operation is not allowed on this device")

    NO_RESOURCES, RIL_NO_RESOURCES ->
      SendFailure("no_resources", "the telephony stack is out of resources")

    CANCELLED, RIL_CANCELLED, RIL_ABORTED ->
      SendFailure("cancelled", "the send was cancelled")

    SMS_BLOCKED_DURING_EMERGENCY ->
      SendFailure("blocked_during_emergency", "sending is blocked during an emergency call")

    NO_DEFAULT_SMS_APP ->
      // Despite the constant's name, on a modern multi-SIM device this is
      // usually an unset default SMS SIM rather than a missing SMS app.
      SendFailure(
        "no_default_sms_app",
        "no SMS subscription could be resolved — set a default SMS SIM in Settings",
      )

    USER_NOT_ALLOWED ->
      SendFailure("user_not_allowed", "this user profile is not allowed to send SMS")

    RIL_SIM_ABSENT, RIL_NO_SUBSCRIPTION ->
      SendFailure("sim_absent", "there is no SIM")

    RIL_SIM_PIN2, RIL_SIM_PUK2, RIL_SUBSCRIPTION_NOT_AVAILABLE, RIL_SIM_ERROR,
    RIL_INVALID_SIM_STATE, RIL_SIM_BUSY, RIL_SIM_FULL,
    ->
      SendFailure("sim_error", "the SIM could not be used")

    NO_BLUETOOTH_SERVICE, INVALID_BLUETOOTH_ADDRESS, BLUETOOTH_DISCONNECTED ->
      SendFailure("generic_failure", "the Bluetooth SMS route failed")

    ERROR_GENERIC_FAILURE, ERROR_NONE, UNEXPECTED_EVENT_STOP_SENDING, RIL_GENERIC_ERROR ->
      SendFailure("generic_failure", "the radio reported an unspecified failure")

    // A code from a platform newer than the table above. Report it verbatim so
    // it can be looked up rather than swallowed.
    else -> SendFailure("generic_failure", "the radio reported result code $resultCode")
  }
}
