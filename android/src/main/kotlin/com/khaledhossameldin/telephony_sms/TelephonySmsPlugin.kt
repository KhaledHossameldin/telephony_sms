package com.khaledhossameldin.telephony_sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.util.concurrent.atomic.AtomicInteger

/** TelephonySmsPlugin */
class TelephonySmsPlugin :
  FlutterPlugin,
  MethodCallHandler,
  PluginRegistry.RequestPermissionsResultListener,
  ActivityAware {

  private var channel: MethodChannel? = null
  private var context: Context? = null

  private var activity: Activity? = null
  private var activityBinding: ActivityPluginBinding? = null

  /** Held between [requestPermission] and [onRequestPermissionsResult]. */
  private var pendingPermissionResult: Result? = null

  /** Sends awaiting their broadcasts, keyed by send id. */
  private val pendingSends = mutableMapOf<String, InFlightSend>()

  /**
   * Request codes must be unique per [PendingIntent]: two PendingIntents that
   * share an action and request code are considered equal and the second reuses
   * the first, because extras are not part of that comparison. A monotonic
   * counter keeps every part of every concurrent send distinct.
   */
  private val requestCodes = AtomicInteger(0)

  private val sendIds = AtomicInteger(0)

  /**
   * Lazy so that merely constructing the plugin does not touch the Android
   * runtime. Only a real send arms a timeout, which keeps the argument- and
   * state-validation paths unit testable on a plain JVM.
   */
  private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

  private var receiver: BroadcastReceiver? = null

  /** A send in flight, plus the bookkeeping needed to finish or abandon it. */
  private class InFlightSend(
    val result: Result,
    val progress: PendingSend,
    val awaitDelivery: Boolean,
    val timeoutRunnable: Runnable,
  ) {
    var sentConfirmed: Boolean = false
    var delivery: PendingSend? = null
  }

  // region FlutterPlugin

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    val context = flutterPluginBinding.applicationContext
    this.context = context
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL_NAME).also {
      it.setMethodCallHandler(this)
    }
    registerReceiver(context)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel?.setMethodCallHandler(null)
    channel = null

    // Anything still waiting on a broadcast can never be answered now, so fail
    // it rather than leaving the Dart future hanging forever.
    for (send in pendingSends.values.toList()) {
      mainHandler.removeCallbacks(send.timeoutRunnable)
      send.result.error(
        ERROR_CANCELLED,
        "The plugin was detached before the send completed.",
        null,
      )
    }
    pendingSends.clear()

    context?.let { unregisterReceiver(it) }
    context = null
  }

  private fun registerReceiver(context: Context) {
    if (receiver != null) return
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(receiverContext: Context?, intent: Intent?) {
        intent ?: return
        onSendBroadcast(intent.action, intent, resultCode)
      }
    }
    this.receiver = receiver

    val filter = IntentFilter().apply {
      addAction(sentAction(context))
      addAction(deliveredAction(context))
    }
    // The flag is mandatory for apps targeting API 34+. These broadcasts come
    // from the telephony stack via our own PendingIntent, so they are not
    // exported.
    ContextCompat.registerReceiver(
      context,
      receiver,
      filter,
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
  }

  private fun unregisterReceiver(context: Context) {
    val receiver = this.receiver ?: return
    runCatching { context.unregisterReceiver(receiver) }
    this.receiver = null
  }

  // endregion

  // region ActivityAware

  override fun onAttachedToActivity(binding: ActivityPluginBinding) = attachActivity(binding)

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) =
    attachActivity(binding)

  override fun onDetachedFromActivity() = detachActivity()

  override fun onDetachedFromActivityForConfigChanges() = detachActivity()

  private fun attachActivity(binding: ActivityPluginBinding) {
    activityBinding = binding
    activity = binding.activity
    binding.addRequestPermissionsResultListener(this)
  }

  private fun detachActivity() {
    activityBinding?.removeRequestPermissionsResultListener(this)
    activityBinding = null
    activity = null
    // A request that is still in flight can never be answered once the activity
    // is gone, so fail it rather than leaving the Dart future hanging forever.
    pendingPermissionResult?.error(
      ERROR_ACTIVITY_DETACHED,
      "The activity was detached before the permission request completed.",
      null,
    )
    pendingPermissionResult = null
  }

  // endregion

  // region MethodCallHandler

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      METHOD_REQUEST_PERMISSION -> requestPermission(result)
      METHOD_SEND_SMS -> sendSms(call, result)
      else -> result.notImplemented()
    }
  }

  private fun requestPermission(result: Result) {
    val context = this.context
    if (context == null) {
      result.error(ERROR_NO_CONTEXT, "The plugin is not attached to a Flutter engine.", null)
      return
    }

    if (hasSendSmsPermission(context)) {
      result.success(true)
      return
    }

    val activity = this.activity
    if (activity == null) {
      result.error(
        ERROR_NO_ACTIVITY,
        "The permission dialog requires an activity, but none is attached.",
        null,
      )
      return
    }

    if (pendingPermissionResult != null) {
      result.error(
        ERROR_ALREADY_REQUESTING,
        "A permission request is already in progress.",
        null,
      )
      return
    }

    pendingPermissionResult = result
    ActivityCompat.requestPermissions(
      activity,
      arrayOf(Manifest.permission.SEND_SMS),
      PERMISSION_REQUEST_CODE,
    )
  }

  private fun sendSms(call: MethodCall, result: Result) {
    val phone = call.argument<String>(ARGUMENT_PHONE)
    if (phone.isNullOrBlank()) {
      result.error(ERROR_INVALID_ARGUMENTS, "\"$ARGUMENT_PHONE\" must be a non-empty string.", null)
      return
    }

    val message = call.argument<String>(ARGUMENT_MESSAGE)
    if (message.isNullOrBlank()) {
      result.error(
        ERROR_INVALID_ARGUMENTS,
        "\"$ARGUMENT_MESSAGE\" must be a non-empty string.",
        null,
      )
      return
    }

    val context = this.context
    if (context == null) {
      result.error(ERROR_NO_CONTEXT, "The plugin is not attached to a Flutter engine.", null)
      return
    }

    if (!hasSendSmsPermission(context)) {
      result.error(
        ERROR_PERMISSION_DENIED,
        "The SEND_SMS permission has not been granted. Call requestPermission() first.",
        null,
      )
      return
    }

    val smsManager = resolveSmsManager(context)
    if (smsManager == null) {
      result.error(
        ERROR_SMS_MANAGER_UNAVAILABLE,
        "This device does not provide an SmsManager.",
        null,
      )
      return
    }

    val awaitDelivery = call.argument<String>(ARGUMENT_COMPLETE_ON) == COMPLETE_ON_DELIVERED
    val timeoutMillis = (call.argument<Number>(ARGUMENT_TIMEOUT_MILLIS)?.toLong() ?: 0L)
      .coerceAtLeast(1_000L)

    val parts = smsManager.divideMessage(message) ?: arrayListOf(message)
    if (parts.isEmpty()) parts.add(message)

    val id = "send-${sendIds.incrementAndGet()}"
    val timeoutRunnable = Runnable { onSendTimedOut(id) }
    val send = InFlightSend(
      result = result,
      progress = PendingSend(id, parts.size),
      awaitDelivery = awaitDelivery,
      timeoutRunnable = timeoutRunnable,
    )
    if (awaitDelivery) send.delivery = PendingSend(id, parts.size)
    pendingSends[id] = send

    val sentIntents = ArrayList<PendingIntent>(parts.size)
    val deliveryIntents = if (awaitDelivery) ArrayList<PendingIntent>(parts.size) else null
    for (index in parts.indices) {
      sentIntents.add(broadcastIntent(context, sentAction(context), id, index))
      deliveryIntents?.add(broadcastIntent(context, deliveredAction(context), id, index))
    }

    try {
      if (parts.size == 1) {
        smsManager.sendTextMessage(
          phone,
          null,
          parts[0],
          sentIntents[0],
          deliveryIntents?.get(0),
        )
      } else {
        smsManager.sendMultipartTextMessage(
          phone,
          null,
          parts,
          sentIntents,
          deliveryIntents,
        )
      }
      mainHandler.postDelayed(timeoutRunnable, timeoutMillis)
    } catch (e: Exception) {
      // Only argument-level problems throw here. Radio failures arrive on the
      // sent broadcast instead, which is the whole reason these intents exist.
      pendingSends.remove(id)
      result.error(ERROR_SEND_FAILED, e.message ?: "Failed to send the SMS message.", null)
    }
  }

  /**
   * A [PendingIntent] that must be mutable: the telephony stack writes the
   * result code and an error code into it, and an immutable one silently
   * discards them.
   */
  private fun broadcastIntent(
    context: Context,
    action: String,
    id: String,
    partIndex: Int,
  ): PendingIntent {
    val intent = Intent(action)
      .setPackage(context.packageName)
      .putExtra(EXTRA_SEND_ID, id)
      .putExtra(EXTRA_PART_INDEX, partIndex)

    var flags = PendingIntent.FLAG_UPDATE_CURRENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      flags = flags or PendingIntent.FLAG_MUTABLE
    }
    return PendingIntent.getBroadcast(
      context,
      requestCodes.incrementAndGet(),
      intent,
      flags,
    )
  }

  private fun onSendBroadcast(action: String?, intent: Intent, resultCode: Int) {
    val context = this.context ?: return
    val id = intent.getStringExtra(EXTRA_SEND_ID) ?: return
    val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
    val send = pendingSends[id] ?: return

    when (action) {
      sentAction(context) -> {
        if (!send.progress.recordPart(partIndex, resultCode)) return

        val outcome = send.progress.outcome
        val errorCode = outcome.errorCode
        if (errorCode != null) {
          finish(id) { it.error(errorCode, outcome.message, null) }
          return
        }

        send.sentConfirmed = true
        // Delivery reports arrive later, and on many carriers never at all, so
        // the timeout stays armed until they do.
        if (!send.awaitDelivery) finish(id) { it.success(null) }
      }

      deliveredAction(context) -> {
        val delivery = send.delivery ?: return
        if (!delivery.recordPart(partIndex, resultCode)) return

        val outcome = delivery.outcome
        if (outcome.isSuccess) {
          finish(id) { it.success(null) }
        } else {
          finish(id) {
            it.error(
              ERROR_NOT_DELIVERED,
              "The message was sent but the carrier reported it as not delivered.",
              null,
            )
          }
        }
      }
    }
  }

  private fun onSendTimedOut(id: String) {
    val send = pendingSends[id] ?: return
    val message = if (send.awaitDelivery && send.sentConfirmed) {
      "The message was sent, but no delivery report arrived before the timeout. " +
        "Many carriers never send delivery reports."
    } else {
      "No send confirmation arrived before the timeout."
    }
    finish(id) { it.error(ERROR_SEND_TIMEOUT, message, null) }
  }

  /** Resolves a send exactly once and clears its timeout. */
  private fun finish(id: String, reply: (Result) -> Unit) {
    val send = pendingSends.remove(id) ?: return
    mainHandler.removeCallbacks(send.timeoutRunnable)
    reply(send.result)
  }

  // endregion

  // region RequestPermissionsResultListener

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray,
  ): Boolean {
    if (requestCode != PERMISSION_REQUEST_CODE) return false

    val granted = grantResults.isNotEmpty() &&
      grantResults[0] == PackageManager.PERMISSION_GRANTED
    pendingPermissionResult?.success(granted)
    pendingPermissionResult = null
    return true
  }

  // endregion

  private fun hasSendSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
      PackageManager.PERMISSION_GRANTED

  /**
   * Resolved on every send rather than cached, so the manager can never go stale
   * against the current permission state.
   *
   * [SmsManager] only became reachable through `getSystemService` in API 31; on
   * anything older that call returns `null` instead of throwing, so the version
   * check has to gate on [Build.VERSION_CODES.S] rather than on the API level
   * that introduced runtime permissions.
   */
  private fun resolveSmsManager(context: Context): SmsManager? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      context.getSystemService(SmsManager::class.java)
    } else {
      @Suppress("DEPRECATION")
      SmsManager.getDefault()
    }

  /** Namespaced per app so two apps using this plugin cannot cross-talk. */
  private fun sentAction(context: Context) = "${context.packageName}.TELEPHONY_SMS_SENT"

  private fun deliveredAction(context: Context) = "${context.packageName}.TELEPHONY_SMS_DELIVERED"

  private companion object {
    const val CHANNEL_NAME = "telephony_sms"
    const val PERMISSION_REQUEST_CODE = 39

    const val METHOD_REQUEST_PERMISSION = "requestPermission"
    const val METHOD_SEND_SMS = "sendSMS"

    const val ARGUMENT_PHONE = "phone"
    const val ARGUMENT_MESSAGE = "message"
    const val ARGUMENT_COMPLETE_ON = "completeOn"
    const val ARGUMENT_TIMEOUT_MILLIS = "timeoutMillis"

    const val COMPLETE_ON_DELIVERED = "delivered"

    const val EXTRA_SEND_ID = "telephony_sms.send_id"
    const val EXTRA_PART_INDEX = "telephony_sms.part_index"

    const val ERROR_INVALID_ARGUMENTS = "invalid_arguments"
    const val ERROR_PERMISSION_DENIED = "permission_denied"
    const val ERROR_NO_ACTIVITY = "no_activity"
    const val ERROR_ACTIVITY_DETACHED = "activity_detached"
    const val ERROR_ALREADY_REQUESTING = "already_requesting"
    const val ERROR_SMS_MANAGER_UNAVAILABLE = "sms_manager_unavailable"
    const val ERROR_SEND_FAILED = "send_failed"
    const val ERROR_NO_CONTEXT = "no_context"
    const val ERROR_SEND_TIMEOUT = "send_timeout"
    const val ERROR_NOT_DELIVERED = "not_delivered"
    const val ERROR_CANCELLED = "cancelled"
  }
}
