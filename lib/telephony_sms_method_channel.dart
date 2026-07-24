import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'telephony_sms_exception.dart';
import 'telephony_sms_platform_interface.dart';

/// An implementation of [TelephonySMSPlatform] that uses method channels.
class MethodChannelTelephonySMS extends TelephonySMSPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('telephony_sms');

  @override
  Future<bool> requestPermission() async {
    try {
      final granted =
          await methodChannel.invokeMethod<bool>('requestPermission');
      return granted ?? false;
    } on PlatformException catch (e) {
      throw _asTelephonySmsException(e);
    }
  }

  @override
  Future<void> sendSMS({
    required String phone,
    required String message,
  }) async {
    try {
      await methodChannel.invokeMethod<void>('sendSMS', {
        'phone': phone,
        'message': message,
      });
    } on PlatformException catch (e) {
      throw _asTelephonySmsException(e);
    }
  }

  TelephonySmsException _asTelephonySmsException(PlatformException e) {
    return TelephonySmsException(
      TelephonySmsErrorCode.fromCode(e.code),
      e.message ?? 'The platform reported an error without a message.',
    );
  }
}
