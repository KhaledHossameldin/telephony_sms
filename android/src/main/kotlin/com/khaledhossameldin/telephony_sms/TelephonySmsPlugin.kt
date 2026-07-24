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
import android.telephony.SmsMessage
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
import java.util.UUID
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
   * Distinguishes this plugin instance's sends from those of any other instance
   * in the same process.
   *
   * The broadcast actions are namespaced per app, not per engine, so every
   * instance sees every instance's broadcasts. Without a per-instance token two
   * engines would both mint "send-1" and each would resolve the other's future.
   */
  private val instanceToken = UUID.randomUUID().toString()

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
    /** Held so they can be released once the send resolves. */
    val intents: List<PendingIntent>,
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

    // Anything still waiting can never be answered now, so fail it rather than
    // leaving the Dart future hanging forever.
    for (id in pendingSends.keys.toList()) {
      finish(id) {
        it.error(ERROR_DETACHED, "The plugin was detached before the send completed.", null)
      }
    }

    pendingPermissionResult?.error(
      ERROR_DETACHED,
      "The plugin was detached before the permission request completed.",
      null,
    )
    pendingPermissionResult = null

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

    // A permission the app never declared can never be granted: the dialog does
    // not appear and the request comes straight back denied, which is
    // indistinguishable from the user saying no.
    if (!isSendSmsDeclared(context)) {
      result.error(ERROR_PERMISSION_NOT_DECLARED, MISSING_PERMISSION_MESSAGE, null)
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
      if (isSendSmsDeclared(context)) {
        result.error(
          ERROR_PERMISSION_DENIED,
          "The SEND_SMS permission has not been granted. Call requestPermission() first.",
          null,
        )
      } else {
        result.error(ERROR_PERMISSION_NOT_DECLARED, MISSING_PERMISSION_MESSAGE, null)
      }
      return
    }

    // Tablets and other Wi-Fi-only devices still hand out an SmsManager, and
    // only fail once the message reaches it.
    if (!hasTelephonyMessaging(context)) {
      result.error(ERROR_NO_TELEPHONY, "This device cannot send SMS messages.", null)
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
      .coerceAtLeast(MINIMUM_TIMEOUT_MILLIS)

    val parts = smsManager.divideMessage(message) ?: arrayListOf(message)
    if (parts.isEmpty()) parts.add(message)
    if (parts.size > MAX_MESSAGE_PARTS) {
      result.error(
        ERROR_INVALID_ARGUMENTS,
        "The message needs ${parts.size} parts, more than the $MAX_MESSAGE_PARTS a single " +
          "multipart message can carry. Send it in smaller pieces.",
        null,
      )
      return
    }

    val id = "$instanceToken/${sendIds.incrementAndGet()}"

    val sentIntents = ArrayList<PendingIntent>(parts.size)
    val deliveryIntents = if (awaitDelivery) ArrayList<PendingIntent>(parts.size) else null
    for (index in parts.indices) {
      sentIntents.add(broadcastIntent(context, sentAction(context), id, index))
      deliveryIntents?.add(broadcastIntent(context, deliveredAction(context), id, index))
    }

    val send = InFlightSend(
      result = result,
      progress = PendingSend(parts.size),
      awaitDelivery = awaitDelivery,
      timeoutRunnable = Runnable { onSendTimedOut(id) },
      intents = sentIntents + (deliveryIntents ?: emptyList()),
    )
    if (awaitDelivery) send.delivery = PendingSend(parts.size)
    pendingSends[id] = send

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
      mainHandler.postDelayed(send.timeoutRunnable, timeoutMillis)
    } catch (e: SecurityException) {
      // The permission was revoked between the check above and this call.
      abandon(id, ERROR_PERMISSION_DENIED, e.message ?: "The SEND_SMS permission was revoked.")
    } catch (e: IllegalArgumentException) {
      // Thrown for a destination address the telephony stack will not accept.
      abandon(id, ERROR_INVALID_ARGUMENTS, e.message ?: "The message arguments were rejected.")
    } catch (e: UnsupportedOperationException) {
      // Thrown from API 36 on devices without FEATURE_TELEPHONY_MESSAGING.
      abandon(id, ERROR_NO_TELEPHONY, e.message ?: "This device cannot send SMS messages.")
    } catch (e: Exception) {
      // Only argument-level problems reach here. Radio failures arrive on the
      // sent broadcast instead, which is the whole reason these intents exist.
      abandon(id, ERROR_SEND_FAILED, e.message ?: "Failed to send the SMS message.")
    }
  }

  /**
   * A [PendingIntent] that must be mutable: the telephony stack writes the
   * result code and the status report into it, and an immutable one silently
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
    // Another engine's send, or one already resolved.
    val send = pendingSends[id] ?: return

    when (action) {
      sentAction(context) -> {
        send.progress.recordPart(partIndex, resultCode)
        if (reportFailure(id, send.progress)) return
        if (!send.progress.isComplete) return

        send.sentConfirmed = true
        // Delivery reports arrive later, and on many carriers never at all, so
        // the timeout stays armed until they do.
        if (!send.awaitDelivery) finish(id) { it.success(null) }
      }

      deliveredAction(context) -> {
        val delivery = send.delivery ?: return
        when (deliveryStateOf(intent, resultCode)) {
          // The carrier is still trying. Say nothing and let the timeout decide.
          DeliveryState.PENDING -> return

          DeliveryState.DELIVERED -> delivery.recordSuccess(partIndex)

          DeliveryState.FAILED -> delivery.recordFailure(
            partIndex,
            ERROR_NOT_DELIVERED,
            "${delivery.describePart(partIndex)} was sent, but the carrier reported it as " +
              "not delivered.",
          )
        }

        if (reportFailure(id, delivery)) return
        if (delivery.isComplete) finish(id) { it.success(null) }
      }
    }
  }

  /**
   * What the carrier's status report says.
   *
   * The report is a PDU in an extra; the broadcast's result code is `RESULT_OK`
   * on every delivery broadcast whatever the report says, so it is only a
   * fallback for a report that is missing or unparseable.
   */
  private fun deliveryStateOf(intent: Intent, resultCode: Int): DeliveryState {
    val pdu = intent.getByteArrayExtra(EXTRA_PDU)
    if (pdu != null) {
      val format = intent.getStringExtra(EXTRA_FORMAT)
      val message = runCatching {
        if (format == null) {
          // The format extra should always be there. Fall back to letting the
          // platform guess rather than dropping the report entirely.
          @Suppress("DEPRECATION")
          SmsMessage.createFromPdu(pdu)
        } else {
          SmsMessage.createFromPdu(pdu, format)
        }
      }.getOrNull()
      if (message != null) return classifyDeliveryStatus(message.status)
    }

    return if (resultCode == SendResultCodes.RESULT_OK) {
      DeliveryState.DELIVERED
    } else {
      DeliveryState.FAILED
    }
  }

  /**
   * Reports [progress]'s failure if it has one, and returns whether it did.
   *
   * Called before checking completeness, so that a failed part resolves the send
   * immediately: when one part fails the radio usually abandons the rest, and
   * waiting for broadcasts that never arrive replaces the real cause with a
   * timeout.
   */
  private fun reportFailure(id: String, progress: PendingSend): Boolean {
    val outcome = progress.outcome
    val errorCode = outcome.errorCode ?: return false
    finish(id) { it.error(errorCode, outcome.message, null) }
    return true
  }

  private fun onSendTimedOut(id: String) {
    val send = pendingSends[id] ?: return
    if (send.awaitDelivery && send.sentConfirmed) {
      finish(id) {
        it.error(
          ERROR_DELIVERY_TIMEOUT,
          "The message was sent, but no delivery report arrived before the timeout. Many " +
            "carriers never send delivery reports. Do not retry: the message went out.",
          null,
        )
      }
    } else {
      finish(id) {
        it.error(ERROR_SEND_TIMEOUT, "No send confirmation arrived before the timeout.", null)
      }
    }
  }

  /** Drops a send that never reached the radio, before any broadcast can arrive. */
  private fun abandon(id: String, errorCode: String, message: String) {
    finish(id) { it.error(errorCode, message, null) }
  }

  /** Resolves a send exactly once, clearing its timeout and releasing its intents. */
  private fun finish(id: String, reply: (Result) -> Unit) {
    val send = pendingSends.remove(id) ?: return
    mainHandler.removeCallbacks(send.timeoutRunnable)
    // Nothing will fire these now, and the system holds them until something
    // does.
    send.intents.forEach { it.cancel() }
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

    val pending = pendingPermissionResult ?: return false

    // The request code alone is not proof the result is ours: another plugin may
    // use the same one. An interrupted request is the exception — it comes back
    // with both arrays empty, and is a denial.
    val index = permissions.indexOf(Manifest.permission.SEND_SMS)
    if (index < 0 && permissions.isNotEmpty()) return false

    pendingPermissionResult = null
    val granted = index >= 0 &&
      index < grantResults.size &&
      grantResults[index] == PackageManager.PERMISSION_GRANTED
    pending.success(granted)
    return true
  }

  // endregion

  private fun hasSendSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
      PackageManager.PERMISSION_GRANTED

  /**
   * Whether the host app's merged manifest declares `SEND_SMS` at all.
   *
   * The plugin does not declare it, so that depending on this package never
   * silently adds a restricted permission to someone's app. The cost is that an
   * app which forgets to declare it sees every request denied with no
   * explanation, which this turns into one.
   *
   * Assumes declared if the lookup fails: refusing to send over a failed
   * introspection would be worse than the problem it detects.
   */
  private fun isSendSmsDeclared(context: Context): Boolean = runCatching {
    val flags = PackageManager.GET_PERMISSIONS
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.PackageInfoFlags.of(flags.toLong()),
      )
    } else {
      @Suppress("DEPRECATION")
      context.packageManager.getPackageInfo(context.packageName, flags)
    }
    info.requestedPermissions?.contains(Manifest.permission.SEND_SMS) == true
  }.getOrDefault(true)

  private fun hasTelephonyMessaging(context: Context): Boolean {
    val packageManager = context.packageManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)
    } else {
      @Suppress("DEPRECATION")
      packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }
  }

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
    const val CHANNEL_NAME = "com.khaledhossameldin/telephony_sms"

    /**
     * Deliberately not a small round number: request codes are a per-activity
     * namespace shared with every other plugin, and a collision would hand this
     * plugin somebody else's permission result.
     *
     * Must fit in 16 bits. `FragmentActivity`, which `FlutterFragmentActivity`
     * extends, rejects anything wider.
     */
    const val PERMISSION_REQUEST_CODE = 0x5353

    /**
     * Request codes must be unique per [PendingIntent]: two PendingIntents that
     * share an action and request code are considered equal and the second
     * reuses the first, because extras are not part of that comparison. The
     * counter is process-wide rather than per-instance so that two engines
     * cannot mint colliding codes.
     */
    val requestCodes = AtomicInteger(0)

    const val METHOD_REQUEST_PERMISSION = "requestPermission"
    const val METHOD_SEND_SMS = "sendSMS"

    const val ARGUMENT_PHONE = "phone"
    const val ARGUMENT_MESSAGE = "message"
    const val ARGUMENT_COMPLETE_ON = "completeOn"
    const val ARGUMENT_TIMEOUT_MILLIS = "timeoutMillis"

    const val COMPLETE_ON_DELIVERED = "delivered"

    const val EXTRA_SEND_ID = "telephony_sms.send_id"
    const val EXTRA_PART_INDEX = "telephony_sms.part_index"

    /** Extras the telephony stack adds to the delivery broadcast. */
    const val EXTRA_PDU = "pdu"
    const val EXTRA_FORMAT = "format"

    const val MINIMUM_TIMEOUT_MILLIS = 1_000L

    /** The platform caps a multipart message at 255 parts. */
    const val MAX_MESSAGE_PARTS = 255

    const val ERROR_INVALID_ARGUMENTS = "invalid_arguments"
    const val ERROR_PERMISSION_DENIED = "permission_denied"
    const val ERROR_PERMISSION_NOT_DECLARED = "permission_not_declared"
    const val ERROR_NO_ACTIVITY = "no_activity"
    const val ERROR_ACTIVITY_DETACHED = "activity_detached"
    const val ERROR_ALREADY_REQUESTING = "already_requesting"
    const val ERROR_SMS_MANAGER_UNAVAILABLE = "sms_manager_unavailable"
    const val ERROR_NO_TELEPHONY = "no_telephony"
    const val ERROR_SEND_FAILED = "send_failed"
    const val ERROR_NO_CONTEXT = "no_context"
    const val ERROR_SEND_TIMEOUT = "send_timeout"
    const val ERROR_DELIVERY_TIMEOUT = "delivery_timeout"
    const val ERROR_NOT_DELIVERED = "not_delivered"
    const val ERROR_DETACHED = "detached"

    const val MISSING_PERMISSION_MESSAGE =
      "This app's manifest does not declare android.permission.SEND_SMS, so the permission can " +
        "never be granted. Add <uses-permission android:name=\"android.permission.SEND_SMS\" /> " +
        "to android/app/src/main/AndroidManifest.xml."
  }
}
