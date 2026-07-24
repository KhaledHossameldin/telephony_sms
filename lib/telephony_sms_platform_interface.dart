import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'telephony_sms_method_channel.dart';

/// The interface that platform-specific implementations of `telephony_sms`
/// must extend.
///
/// Implementations should extend this class rather than implement it, so that
/// new members are inherited rather than breaking the build.
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

  /// Requests the `SEND_SMS` permission, resolving to whether it was granted.
  Future<bool> requestPermission() {
    throw UnimplementedError('requestPermission() has not been implemented.');
  }

  /// Sends [message] to [phone].
  Future<void> sendSMS({
    required String phone,
    required String message,
  }) async {
    throw UnimplementedError('sendSMS() has not been implemented.');
  }
}
