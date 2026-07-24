import 'telephony_sms_platform_interface.dart';

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
  /// Completes once the message has been handed to the radio. Throws a
  /// [TelephonySmsException] if the permission is missing, the arguments are
  /// invalid, or the platform rejected the message.
  ///
  /// Messages longer than a single SMS part (160 GSM-7 characters, or 70 when
  /// any character falls outside GSM-7 — Arabic and emoji do) are truncated.
  Future<void> sendSMS({
    required String phone,
    required String message,
  }) {
    return TelephonySMSPlatform.instance.sendSMS(
      phone: phone,
      message: message,
    );
  }
}
