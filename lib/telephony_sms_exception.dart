/// The reason a [TelephonySmsException] was thrown.
///
/// Values fall into three groups: failures raised by the plugin before the
/// message reached the radio, failures the radio itself reported, and the state
/// of the send afterwards.
///
/// Android defines around eighty distinct send result codes. They are grouped
/// here by what a caller can do about them rather than kept one-to-one, so
/// several platform codes can produce the same value.
enum TelephonySmsErrorCode {
  // Raised by the plugin, before anything reached the radio.

  /// `phone` or `message` was null, empty, blank, or rejected by the platform.
  ///
  /// Also raised when the message is too long to fit in one multipart message.
  invalidArguments('invalid_arguments'),

  /// `SEND_SMS` has not been granted. Call `requestPermission()` first.
  permissionDenied('permission_denied'),

  /// The host app's manifest does not declare `SEND_SMS`.
  ///
  /// The permission can never be granted until it does, so the dialog never
  /// appears and every request comes straight back denied. Add
  /// `<uses-permission android:name="android.permission.SEND_SMS" />` to
  /// `android/app/src/main/AndroidManifest.xml`.
  permissionNotDeclared('permission_not_declared'),

  /// No activity was attached, so the permission dialog could not be shown.
  noActivity('no_activity'),

  /// The activity went away before the permission request was answered.
  activityDetached('activity_detached'),

  /// A permission request was already in flight.
  alreadyRequesting('already_requesting'),

  /// The device does not provide an `SmsManager`.
  smsManagerUnavailable('sms_manager_unavailable'),

  /// The device cannot send SMS at all — a tablet or other Wi-Fi-only device.
  noTelephony('no_telephony'),

  /// No default SMS subscription is set on a device with more than one SIM.
  ///
  /// Sending would otherwise make the system interrupt with a SIM-picker dialog
  /// in the foreground, or fail outright in the background, so the plugin refuses
  /// up front. The user must set a default under Settings → Network & internet →
  /// SIMs → SMS. Not retryable until they do.
  noSmsSubscription('no_sms_subscription'),

  /// The plugin could not register the receiver it needs to confirm sends.
  ///
  /// Without it no send can ever be confirmed, so sending is refused rather than
  /// done blind. Two things cause it: a merged manifest missing the
  /// `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` that
  /// androidx.core normally contributes, or an androidx.core resolved below
  /// 1.9.0. Not retryable — both are build-configuration problems.
  receiverUnavailable('receiver_unavailable'),

  /// `SmsManager` rejected the message before it reached the radio.
  sendFailed('send_failed'),

  /// The plugin is not attached to a Flutter engine.
  noContext('no_context'),

  /// This platform has no implementation of the plugin.
  ///
  /// `telephony_sms` is Android only: iOS does not permit sending SMS without
  /// user interaction, and there is no equivalent of `SmsManager`.
  unsupportedPlatform('unsupported_platform'),

  // Reported by the radio.

  /// The device has no cellular service. Safe to retry once service returns.
  noService('no_service'),

  /// The radio is off — airplane mode, most likely. Safe to retry once it is on.
  radioOff('radio_off'),

  /// The radio is not available. Usually transient.
  radioNotAvailable('radio_not_available'),

  /// The telephony stack produced a null PDU. Not retryable as-is.
  nullPdu('null_pdu'),

  /// The radio reported an unspecified failure. Often transient.
  genericFailure('generic_failure'),

  /// The device's SMS rate limit was reached.
  ///
  /// Android throttles how many messages an app may send in a window, so this is
  /// the usual failure when sending in a loop. Back off and retry rather than
  /// retrying immediately.
  limitExceeded('limit_exceeded'),

  /// The number is not in the SIM's fixed dialling list.
  fdnCheckFailure('fdn_check_failure'),

  /// Sending to this short code was not permitted, typically a premium number
  /// the user declined to pay for.
  shortCodeNotAllowed('short_code_not_allowed'),

  /// Sending to this short code is never permitted.
  shortCodeNeverAllowed('short_code_never_allowed'),

  /// The network rejected the message.
  networkReject('network_reject'),

  /// The network could not carry the message. Usually transient.
  networkError('network_error'),

  /// The modem reported an error. Often transient.
  modemError('modem_error'),

  /// The device is out of memory.
  noMemory('no_memory'),

  /// The telephony stack is out of resources.
  noResources('no_resources'),

  /// The telephony system reported an internal error.
  systemError('system_error'),

  /// The message format is invalid.
  invalidSmsFormat('invalid_sms_format'),

  /// The message could not be encoded for the network.
  encodingError('encoding_error'),

  /// The SMS centre address is invalid.
  invalidSmscAddress('invalid_smsc_address'),

  /// The operation is not allowed on this device.
  operationNotAllowed('operation_not_allowed'),

  /// Sending is blocked while an emergency call is in progress.
  blockedDuringEmergency('blocked_during_emergency'),

  /// No SMS subscription could be resolved for the send.
  ///
  /// Despite the platform name, on a modern multi-SIM device this usually means
  /// no default SMS SIM is set rather than a missing default SMS app. Set one
  /// under Settings → Network & internet → SIMs → SMS. See [noSmsSubscription],
  /// which the plugin raises up front for the same underlying cause.
  noDefaultSmsApp('no_default_sms_app'),

  /// This user profile is not allowed to send SMS.
  userNotAllowed('user_not_allowed'),

  /// There is no SIM.
  simAbsent('sim_absent'),

  /// The SIM could not be used — locked, busy, full, or in an invalid state.
  simError('sim_error'),

  /// The send was cancelled by the platform.
  cancelled('cancelled'),

  // The state of the send afterwards.

  /// No send confirmation arrived before the timeout.
  ///
  /// This means the plugin **could not determine** whether the message went out.
  /// It may have. For that reason it is deliberately **not** [isTransient]: an
  /// automatic retry would send the message a second time whenever it did go out.
  /// Decide per message whether a possible duplicate is acceptable before
  /// retrying by hand — and if it is not, treat this as a failure to surface, not
  /// to retry.
  sendTimeout('send_timeout'),

  /// The message was sent, but no delivery report arrived before the timeout.
  ///
  /// This is the common outcome of `SmsCompletion.delivered`: many carriers
  /// never emit delivery reports. **Do not retry** — the message went out.
  deliveryTimeout('delivery_timeout'),

  /// The message was sent, but the carrier reported it as not delivered.
  notDelivered('not_delivered'),

  /// The plugin detached from the engine while the call was in flight.
  detached('detached'),

  /// The platform reported a code this version of the plugin does not know.
  unknown('unknown');

  const TelephonySmsErrorCode(this.code);

  /// The raw string used on the method channel.
  final String code;

  /// Whether retrying the same send could plausibly succeed later.
  ///
  /// Covers conditions that change on their own — no service, a radio that is
  /// off, a busy network, a rate limit. It does not cover anything the user or
  /// the app has to fix first, such as a denied permission or a barred number.
  ///
  /// The timeouts are deliberately excluded. [sendTimeout] means the plugin could
  /// not tell whether the message went out, so an automatic retry risks a
  /// duplicate; [deliveryTimeout] means it *was* sent. Both cost money to retry
  /// blindly, so both are left for the caller to decide about explicitly.
  bool get isTransient => switch (this) {
    noService ||
    radioOff ||
    radioNotAvailable ||
    genericFailure ||
    limitExceeded ||
    networkReject ||
    networkError ||
    modemError ||
    noMemory ||
    noResources ||
    systemError => true,
    _ => false,
  };

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
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is TelephonySmsException &&
          other.code == code &&
          other.message == message;

  @override
  int get hashCode => Object.hash(code, message);

  @override
  String toString() => 'TelephonySmsException(${code.code}): $message';
}
