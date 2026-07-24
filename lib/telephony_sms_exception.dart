/// The reason a [TelephonySmsException] was thrown.
///
/// Each value maps to an error code raised by the Android side of the plugin.
enum TelephonySmsErrorCode {
  /// `phone` or `message` was null, empty, or blank.
  invalidArguments('invalid_arguments'),

  /// `SEND_SMS` has not been granted. Call `requestPermission()` first.
  permissionDenied('permission_denied'),

  /// No activity was attached, so the permission dialog could not be shown.
  noActivity('no_activity'),

  /// The activity went away before the permission request was answered.
  activityDetached('activity_detached'),

  /// A permission request was already in flight.
  alreadyRequesting('already_requesting'),

  /// The device does not provide an `SmsManager`.
  smsManagerUnavailable('sms_manager_unavailable'),

  /// `SmsManager` rejected the message.
  sendFailed('send_failed'),

  /// The plugin is not attached to a Flutter engine.
  noContext('no_context'),

  /// The platform reported a code this version of the plugin does not know.
  unknown('unknown');

  const TelephonySmsErrorCode(this.code);

  /// The raw string used on the method channel.
  final String code;

  /// Maps a platform error code onto its enum value, falling back to [unknown].
  static TelephonySmsErrorCode fromCode(String? code) {
    for (final value in TelephonySmsErrorCode.values) {
      if (value.code == code) return value;
    }
    return TelephonySmsErrorCode.unknown;
  }
}

/// Thrown when the platform side of the plugin fails.
class TelephonySmsException implements Exception {
  /// Creates an exception with an explicit [code] and [message].
  const TelephonySmsException(this.code, this.message);

  /// Why the call failed.
  final TelephonySmsErrorCode code;

  /// A human-readable description of the failure.
  final String message;

  @override
  String toString() => 'TelephonySmsException(${code.code}): $message';
}
