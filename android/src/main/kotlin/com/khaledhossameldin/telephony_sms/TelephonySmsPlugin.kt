package com.khaledhossameldin.telephony_sms

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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

  // region FlutterPlugin

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL_NAME).also {
      it.setMethodCallHandler(this)
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel?.setMethodCallHandler(null)
    channel = null
    context = null
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

    try {
      smsManager.sendTextMessage(phone, null, message, null, null)
      result.success(null)
    } catch (e: Exception) {
      result.error(ERROR_SEND_FAILED, e.message ?: "Failed to send the SMS message.", null)
    }
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

  private companion object {
    const val CHANNEL_NAME = "telephony_sms"
    const val PERMISSION_REQUEST_CODE = 39

    const val METHOD_REQUEST_PERMISSION = "requestPermission"
    const val METHOD_SEND_SMS = "sendSMS"

    const val ARGUMENT_PHONE = "phone"
    const val ARGUMENT_MESSAGE = "message"

    const val ERROR_INVALID_ARGUMENTS = "invalid_arguments"
    const val ERROR_PERMISSION_DENIED = "permission_denied"
    const val ERROR_NO_ACTIVITY = "no_activity"
    const val ERROR_ACTIVITY_DETACHED = "activity_detached"
    const val ERROR_ALREADY_REQUESTING = "already_requesting"
    const val ERROR_SMS_MANAGER_UNAVAILABLE = "sms_manager_unavailable"
    const val ERROR_SEND_FAILED = "send_failed"
    const val ERROR_NO_CONTEXT = "no_context"
  }
}
