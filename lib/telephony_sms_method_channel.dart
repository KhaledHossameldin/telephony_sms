import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'telephony_sms_completion.dart';
import 'telephony_sms_exception.dart';
import 'telephony_sms_platform_interface.dart';

/// An implementation of [TelephonySMSPlatform] that uses method channels.
class MethodChannelTelephonySMS extends TelephonySMSPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel(
    'com.khaledhossameldin/telephony_sms',
  );

  @override
  Future<bool> requestPermission() async {
    try {
      final granted = await methodChannel.invokeMethod<bool>(
        'requestPermission',
      );
      return granted ?? false;
    } on PlatformException catch (e) {
      throw _asTelephonySmsException(e);
    } on MissingPluginException {
      throw _unsupportedPlatform();
    }
  }

  @override
  Future<void> sendSMS({
    required String phone,
    required String message,
    SmsCompletion completeOn = SmsCompletion.sent,
    Duration timeout = const Duration(seconds: 30),
  }) async {
    try {
      await methodChannel.invokeMethod<void>('sendSMS', {
        'phone': phone,
        'message': message,
        'completeOn': completeOn.value,
        'timeoutMillis': timeout.inMilliseconds,
      });
    } on PlatformException catch (e) {
      throw _asTelephonySmsException(e);
    } on MissingPluginException {
      throw _unsupportedPlatform();
    }
  }

  TelephonySmsException _asTelephonySmsException(PlatformException e) {
    return TelephonySmsException(
      TelephonySmsErrorCode.fromCode(e.code),
      e.message ?? 'The platform reported an error without a message.',
    );
  }

  /// Raised when no platform implementation answered the channel.
  ///
  /// `MissingPluginException` is not a [PlatformException], so without this it
  /// would escape untyped on every platform but Android and break the promise
  /// that failures arrive as a [TelephonySmsException].
  TelephonySmsException _unsupportedPlatform() {
    return const TelephonySmsException(
      TelephonySmsErrorCode.unsupportedPlatform,
      'telephony_sms has no implementation on this platform. It supports '
      'Android only.',
    );
  }
}
