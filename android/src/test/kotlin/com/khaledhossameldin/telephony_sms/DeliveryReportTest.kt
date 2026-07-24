package com.khaledhossameldin.telephony_sms

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the delivery classification, which is the part of delivery reporting
 * with real logic in it.
 *
 * The plugin previously read the broadcast's result code, which the framework
 * sets to `RESULT_OK` on every delivery broadcast — so a permanently failed
 * delivery looked identical to a successful one. These cases pin the status
 * values that distinguish them.
 */
internal class DeliveryReportTest {

  // GSM: the TP-Status octet, TS 23.040 §9.2.3.15.

  @Test
  fun gsm_receivedBySme_isDelivered() {
    assertEquals(DeliveryState.DELIVERED, classifyDeliveryStatus(0x00))
  }

  @Test
  fun gsm_topOfCompletedRange_isDelivered() {
    assertEquals(DeliveryState.DELIVERED, classifyDeliveryStatus(GSM_COMPLETED_MAX))
  }

  @Test
  fun gsm_temporaryErrorStillTrying_isPending() {
    assertEquals(DeliveryState.PENDING, classifyDeliveryStatus(0x20))
    assertEquals(DeliveryState.PENDING, classifyDeliveryStatus(GSM_TEMPORARY_MAX))
  }

  @Test
  fun gsm_permanentError_isFailed() {
    assertEquals(DeliveryState.FAILED, classifyDeliveryStatus(0x40))
    assertEquals(DeliveryState.FAILED, classifyDeliveryStatus(0x5F))
  }

  /** 0x60..0x7F is a temporary error the service centre has stopped retrying. */
  @Test
  fun gsm_temporaryErrorGivenUpOn_isFailed() {
    assertEquals(DeliveryState.FAILED, classifyDeliveryStatus(0x60))
    assertEquals(DeliveryState.FAILED, classifyDeliveryStatus(0x7F))
  }

  // CDMA: shifted into bits 31-16, error class in bits 25-24.

  @Test
  fun cdma_delivered_isDelivered() {
    // The one value SmsMessage.getStatus() documents as "received".
    assertEquals(DeliveryState.DELIVERED, classifyDeliveryStatus(CDMA_STATUS_DELIVERED shl 16))
  }

  /**
   * "Deposited to internet": no error, but not at the handset yet.
   *
   * Status code 0, "accepted", is not covered here because error class 0 with
   * status code 0 is the integer 0, which is also GSM's "received by the SME".
   * Nothing in the value distinguishes them, so 0 keeps the GSM meaning.
   */
  @Test
  fun cdma_deposited_isPending() {
    assertEquals(DeliveryState.PENDING, classifyDeliveryStatus(1 shl 16))
  }

  @Test
  fun cdma_cancelled_isFailed() {
    assertEquals(DeliveryState.FAILED, classifyDeliveryStatus(CDMA_STATUS_CANCELLED shl 16))
  }

  @Test
  fun cdma_temporaryErrorClass_isPending() {
    val status = (CDMA_ERROR_CLASS_TEMPORARY shl 24) or (4 shl 16)

    assertEquals(DeliveryState.PENDING, classifyDeliveryStatus(status))
  }

  /**
   * A CDMA permanent failure has a zero low octet, so reading it as a GSM
   * TP-Status would report the failure as a delivery.
   */
  @Test
  fun cdma_permanentErrorClass_isFailed() {
    val status = (3 shl 24) or (5 shl 16)

    assertEquals(0, status and 0xFF)
    assertEquals(DeliveryState.FAILED, classifyDeliveryStatus(status))
  }
}
