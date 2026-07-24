package com.khaledhossameldin.telephony_sms

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlin.test.Test
import kotlin.test.assertFalse
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito

/*
 * Unit tests for the argument-validation and reply paths of the plugin that do
 * not need an Android runtime. Everything asserted here runs before the plugin
 * touches the framework, so it is reachable with a detached plugin instance.
 *
 * Once you have built the plugin's example app, you can run these tests from the
 * command line by running `./gradlew testDebugUnitTest` in the `example/android/`
 * directory, or from IDEs that support JUnit such as Android Studio.
 */
internal class TelephonySmsPluginTest {

  private fun mockResult(): MethodChannel.Result =
    Mockito.mock(MethodChannel.Result::class.java)

  @Test
  fun onMethodCall_unknownMethod_repliesNotImplemented() {
    val plugin = TelephonySmsPlugin()
    val result = mockResult()

    plugin.onMethodCall(MethodCall("getPlatformVersion", null), result)

    Mockito.verify(result).notImplemented()
    Mockito.verify(result, Mockito.never()).success(any())
    Mockito.verify(result, Mockito.never()).error(anyString(), anyString(), any())
  }

  @Test
  fun sendSMS_withoutArguments_repliesInvalidArgumentsExactlyOnce() {
    val plugin = TelephonySmsPlugin()
    val result = mockResult()

    plugin.onMethodCall(MethodCall("sendSMS", null), result)

    Mockito.verify(result, Mockito.times(1))
      .error(eq("invalid_arguments"), anyString(), Mockito.isNull())
    Mockito.verify(result, Mockito.never()).success(any())
  }

  @Test
  fun sendSMS_withBlankPhone_repliesInvalidArguments() {
    val plugin = TelephonySmsPlugin()
    val result = mockResult()

    val call = MethodCall("sendSMS", mapOf("phone" to "   ", "message" to "hello"))
    plugin.onMethodCall(call, result)

    Mockito.verify(result, Mockito.times(1))
      .error(eq("invalid_arguments"), anyString(), Mockito.isNull())
    Mockito.verify(result, Mockito.never()).success(any())
  }

  @Test
  fun sendSMS_withMissingMessage_repliesInvalidArguments() {
    val plugin = TelephonySmsPlugin()
    val result = mockResult()

    val call = MethodCall("sendSMS", mapOf("phone" to "+201000000000"))
    plugin.onMethodCall(call, result)

    Mockito.verify(result, Mockito.times(1))
      .error(eq("invalid_arguments"), anyString(), Mockito.isNull())
    Mockito.verify(result, Mockito.never()).success(any())
  }

  /**
   * Regression guard: an earlier version replied twice on this path — once with
   * an error and again from the catch block after dereferencing a null
   * SmsManager — which crashes the engine with "Reply already submitted".
   */
  @Test
  fun sendSMS_whenDetached_repliesExactlyOnce() {
    val plugin = TelephonySmsPlugin()
    val result = mockResult()

    val call = MethodCall(
      "sendSMS",
      mapOf("phone" to "+201000000000", "message" to "hello"),
    )
    plugin.onMethodCall(call, result)

    Mockito.verify(result, Mockito.times(1))
      .error(eq("no_context"), anyString(), Mockito.isNull())
    Mockito.verify(result, Mockito.never()).success(any())
    Mockito.verify(result, Mockito.never()).notImplemented()
  }

  @Test
  fun requestPermission_whenDetached_repliesExactlyOnce() {
    val plugin = TelephonySmsPlugin()
    val result = mockResult()

    plugin.onMethodCall(MethodCall("requestPermission", null), result)

    Mockito.verify(result, Mockito.times(1))
      .error(eq("no_context"), anyString(), Mockito.isNull())
    Mockito.verify(result, Mockito.never()).success(any())
  }

  @Test
  fun onRequestPermissionsResult_foreignRequestCode_isNotHandled() {
    val plugin = TelephonySmsPlugin()

    val handled = plugin.onRequestPermissionsResult(
      /* requestCode = */ 1234,
      /* permissions = */ arrayOf(SEND_SMS),
      /* grantResults = */ intArrayOf(0),
    )

    assertFalse(handled)
  }

  /**
   * Request codes are an activity-wide namespace shared with every other plugin.
   * Matching on the code alone would let this plugin resolve its pending result
   * with somebody else's grant array.
   */
  @Test
  fun onRequestPermissionsResult_otherPluginsPermission_isNotHandled() {
    val plugin = TelephonySmsPlugin()

    val handled = plugin.onRequestPermissionsResult(
      PLUGIN_REQUEST_CODE,
      arrayOf("android.permission.CAMERA"),
      intArrayOf(0),
    )

    assertFalse(handled)
  }

  /** Nothing is pending on a fresh plugin, so there is nothing to resolve. */
  @Test
  fun onRequestPermissionsResult_withNothingPending_isNotHandled() {
    val plugin = TelephonySmsPlugin()

    val handled = plugin.onRequestPermissionsResult(
      PLUGIN_REQUEST_CODE,
      arrayOf(SEND_SMS),
      intArrayOf(0),
    )

    assertFalse(handled)
  }

  private companion object {
    const val SEND_SMS = "android.permission.SEND_SMS"

    /** Mirrors `TelephonySmsPlugin.PERMISSION_REQUEST_CODE`, which is private. */
    const val PLUGIN_REQUEST_CODE = 0x5353
  }
}
