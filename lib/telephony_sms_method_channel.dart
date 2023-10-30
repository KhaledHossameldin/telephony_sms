import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'telephony_sms_platform_interface.dart';

/// An implementation of [TelephonySMSPlatform] that uses method channels.
class MethodChannelTelephonySMS extends TelephonySMSPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('telephony_sms');

  @override
  Future<void> requestPermission() async {
    await methodChannel.invokeMethod('requestPermission');
  }

  @override
  Future<void> sendSMS({
    required String phone,
    required String message,
  }) async {
    await methodChannel.invokeMethod('sendSMS', {
      "phone": phone,
      "message": message,
    });
  }
}
