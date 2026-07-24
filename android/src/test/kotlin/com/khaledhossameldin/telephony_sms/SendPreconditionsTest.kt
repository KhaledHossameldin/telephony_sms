package com.khaledhossameldin.telephony_sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the order in which a send is refused. Each refusal exists to keep the
 * plugin from sending a message it can never confirm — which is what pushes a
 * caller into a retry and a user into paying twice — so the precedence between
 * them is worth pinning.
 */
internal class SendPreconditionsTest {

  @Test
  fun allConditionsGood_permitsTheSend() {
    val block = SendPreconditions.blockFor(
      receiverFailure = null,
      hasTelephony = true,
      hasSmsSubscription = true,
    )

    assertNull(block)
  }

  @Test
  fun aReceiverFailure_blocksAndNamesTheCause() {
    val block = SendPreconditions.blockFor(
      receiverFailure = "NoSuchMethodError: registerReceiver",
      hasTelephony = true,
      hasSmsSubscription = true,
    )

    assertEquals(SendPreconditions.ERROR_RECEIVER_UNAVAILABLE, block?.code)
    // The underlying failure has to reach the caller: logcat is the only other
    // place it appears.
    assertEquals(true, block?.message?.contains("NoSuchMethodError: registerReceiver"))
  }

  @Test
  fun noTelephony_blocks() {
    val block = SendPreconditions.blockFor(
      receiverFailure = null,
      hasTelephony = false,
      hasSmsSubscription = true,
    )

    assertEquals(SendPreconditions.ERROR_NO_TELEPHONY, block?.code)
  }

  @Test
  fun noSmsSubscription_blocks() {
    val block = SendPreconditions.blockFor(
      receiverFailure = null,
      hasTelephony = true,
      hasSmsSubscription = false,
    )

    assertEquals(SendPreconditions.ERROR_NO_SMS_SUBSCRIPTION, block?.code)
  }

  /**
   * A dead receiver is the most fundamental failure — nothing can be confirmed —
   * so it is reported even when other conditions are also unmet.
   */
  @Test
  fun receiverFailure_outranksTheOtherBlocks() {
    val block = SendPreconditions.blockFor(
      receiverFailure = "RuntimeException",
      hasTelephony = false,
      hasSmsSubscription = false,
    )

    assertEquals(SendPreconditions.ERROR_RECEIVER_UNAVAILABLE, block?.code)
  }

  @Test
  fun telephony_outranksSubscription() {
    val block = SendPreconditions.blockFor(
      receiverFailure = null,
      hasTelephony = false,
      hasSmsSubscription = false,
    )

    // A device that cannot send SMS at all should say so, not complain about a
    // SIM it has no use for.
    assertEquals(SendPreconditions.ERROR_NO_TELEPHONY, block?.code)
  }
}
