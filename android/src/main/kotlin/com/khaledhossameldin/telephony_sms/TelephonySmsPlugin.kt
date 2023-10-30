package com.khaledhossameldin.telephony_sms

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
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
class TelephonySmsPlugin: FlutterPlugin, MethodCallHandler, PluginRegistry.RequestPermissionsResultListener, ActivityAware {

  private lateinit var channel : MethodChannel

  private val permissionCode : Int = 39

  private var currentActivity : Activity? = null
  private var context: Context? = null

  private var smsManager : SmsManager? = null

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "telephony_sms")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    if (call.method == "requestPermission") {
      requestPermission(result)
    } else if (call.method == "sendSMS") {
      if (smsManager == null) {
        result.error("Permission Denied", "This plugin requires Sending SMS permission in order to work", Exception("This plugin requires Sending SMS permission in order to work"))
      }
      try {
        val phone = (call.arguments as Map<String, String>)["phone"]!!
        val message = (call.arguments as Map<String, String>)["message"]!!
        smsManager!!.sendTextMessage(phone, null, message, null, null)

      } catch (e: Exception) {
        result.error("Error while sending SMS", "An error has occurred", e)
      }
    } else {
      result.notImplemented()
    }
  }

  private fun initSMSManager() {
    smsManager = try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context!!.getSystemService(SmsManager::class.java)
      } else {
        SmsManager.getDefault();
      }
    } catch (_: Exception) {
      SmsManager.getDefault();
    }
  }

  private fun requestPermission(result: Result) {
    when {
        ContextCompat.checkSelfPermission(
          context!!,
          Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED -> {
          result.success(true)
        }
        shouldShowRequestPermissionRationale(currentActivity!!, Manifest.permission.SEND_SMS) -> {
          result.error("Permission Denied", "This plugin requires Sending SMS permission in order to work", Exception("This plugin requires Sending SMS permission in order to work"))
        } else -> {
        ActivityCompat.requestPermissions(currentActivity!!, arrayOf(Manifest.permission.SEND_SMS), permissionCode)
      }
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ): Boolean {
    when (requestCode) {
      permissionCode -> {
        if (grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED) {
          initSMSManager()
        }
        return true
      }
    }
    return false
  }

  override fun onDetachedFromActivity() {
    currentActivity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    currentActivity = binding.activity
    if (ContextCompat.checkSelfPermission(binding.activity, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
      initSMSManager()
    }
    binding.addRequestPermissionsResultListener(this)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    currentActivity = binding.activity
    if (ContextCompat.checkSelfPermission(binding.activity, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
      initSMSManager()
    }
    binding.addRequestPermissionsResultListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    currentActivity = null
  }

}
