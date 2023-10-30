import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'telephony_sms_method_channel.dart';

abstract class TelephonySMSPlatform extends PlatformInterface {
  /// Constructs a TelephonySmsPlatform.
  TelephonySMSPlatform() : super(token: _token);

  static final Object _token = Object();

  static TelephonySMSPlatform _instance = MethodChannelTelephonySMS();

  /// The default instance of [TelephonySMSPlatform] to use.
  ///
  /// Defaults to [MethodChannelTelephonySMS].
  static TelephonySMSPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [TelephonySMSPlatform] when
  /// they register themselves.
  static set instance(TelephonySMSPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<void> requestPermission() {
    throw UnimplementedError('requestPermission() has not been implemented.');
  }

  Future<void> sendSMS({
    required String phone,
    required String message,
  }) async {
    throw UnimplementedError('sendSMS() has not been implemented.');
  }
}
