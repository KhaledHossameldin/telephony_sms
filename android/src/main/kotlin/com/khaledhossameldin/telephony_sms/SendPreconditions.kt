package com.khaledhossameldin.telephony_sms

/** A reason a send was refused before anything reached the radio. */
internal data class SendBlock(val code: String, val message: String)

/**
 * Decides whether a send may be attempted at all.
 *
 * Every condition here is one where attempting the send is worse than refusing it.
 * Sending costs the user money, and the failures below all end with the plugin
 * unable to say whether the message went out — which pushes the caller into a
 * retry, and the user into paying twice. Refusing is the cheaper mistake.
 *
 * Android-free so the precedence between these conditions is unit testable; the
 * plugin only gathers the facts.
 */
internal object SendPreconditions {

  const val ERROR_RECEIVER_UNAVAILABLE = "receiver_unavailable"
  const val ERROR_NO_TELEPHONY = "no_telephony"
  const val ERROR_NO_SMS_SUBSCRIPTION = "no_sms_subscription"

  /**
   * The reason a send must be refused, or null when it may proceed.
   *
   * @param receiverFailure why the result receiver could not be registered, or null
   *   if it registered. Checked first: without it no send can ever be confirmed.
   * @param hasTelephony whether the device can send SMS at all.
   * @param hasSmsSubscription whether a usable default SMS subscription exists.
   */
  fun blockFor(
    receiverFailure: String?,
    hasTelephony: Boolean,
    hasSmsSubscription: Boolean,
  ): SendBlock? = when {
    receiverFailure != null -> SendBlock(
      ERROR_RECEIVER_UNAVAILABLE,
      "The plugin could not register its result receiver, so no send can be " +
        "confirmed and sending is refused rather than done blind. This usually means " +
        "the merged manifest is missing the <permission> and <uses-permission> for " +
        "\${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION, which " +
        "androidx.core normally contributes, or that androidx.core resolved below " +
        "1.9.0. Underlying failure: $receiverFailure",
    )

    !hasTelephony -> SendBlock(
      ERROR_NO_TELEPHONY,
      "This device cannot send SMS messages.",
    )

    !hasSmsSubscription -> SendBlock(
      ERROR_NO_SMS_SUBSCRIPTION,
      "No default SMS subscription is set, so the message cannot be sent without " +
        "the system interrupting to ask which SIM to use — and in the background it " +
        "would fail outright. Set a default under Settings > Network & internet > " +
        "SIMs > SMS.",
    )

    else -> null
  }
}
