import 'telephony_sms_completion.dart';
import 'telephony_sms_platform_interface.dart';

export 'telephony_sms_completion.dart';
export 'telephony_sms_exception.dart';

/// Sends SMS messages in the background on Android.
class TelephonySMS {
  /// Requests the `SEND_SMS` runtime permission.
  ///
  /// Returns `true` if the permission is granted, `false` if the user denied it.
  /// Resolves immediately with `true` when the permission was already granted.
  ///
  /// Throws a [TelephonySmsException] if the request could not be made at all —
  /// for example when no activity is attached.
  Future<bool> requestPermission() {
    return TelephonySMSPlatform.instance.requestPermission();
  }

  /// Sends [message] to [phone].
  ///
  /// By default this completes once the radio confirms the message was sent, and
  /// throws a [TelephonySmsException] otherwise. That makes a retry loop
  /// straightforward:
  ///
  /// ```dart
  /// try {
  ///   await telephonySMS.sendSMS(phone: phone, message: message);
  /// } on TelephonySmsException catch (e) {
  ///   if (e.code == TelephonySmsErrorCode.noService ||
  ///       e.code == TelephonySmsErrorCode.radioOff) {
  ///     // Transient — worth retrying later.
  ///   }
  /// }
  /// ```
  ///
  /// Pass [completeOn] as `SmsCompletion.delivered` to wait for the carrier's
  /// delivery report instead. Read that value's documentation first: many
  /// carriers never send one.
  ///
  /// Messages longer than a single SMS part are split and sent as a multipart
  /// message. The send succeeds only if every part does.
  ///
  /// [timeout] bounds the wait for confirmation, so a broadcast that never
  /// arrives cannot hang the future. Values below one second are raised to one
  /// second.
  Future<void> sendSMS({
    required String phone,
    required String message,
    SmsCompletion completeOn = SmsCompletion.sent,
    Duration timeout = const Duration(seconds: 30),
  }) {
    return TelephonySMSPlatform.instance.sendSMS(
      phone: phone,
      message: message,
      completeOn: completeOn,
      timeout: timeout,
    );
  }
}
