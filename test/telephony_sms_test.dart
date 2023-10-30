import 'package:flutter_test/flutter_test.dart';
import 'package:telephony_sms/telephony_sms.dart';
import 'package:telephony_sms/telephony_sms_platform_interface.dart';
import 'package:telephony_sms/telephony_sms_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockTelephonySMSPlatform
    with MockPlatformInterfaceMixin
    implements TelephonySMSPlatform {
  @override
  Future<void> requestPermission() =>
      Future.delayed(const Duration(seconds: 2));

  @override
  Future<void> sendSMS({required String phone, required String message}) =>
      Future.delayed(const Duration(seconds: 2));
}

void main() {
  final TelephonySMSPlatform initialPlatform = TelephonySMSPlatform.instance;

  test('$MethodChannelTelephonySMS is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelTelephonySMS>());
  });

  test('requestPermission', () async {
    TelephonySMS telephonySMSPlugin = TelephonySMS();
    MockTelephonySMSPlatform fakePlatform = MockTelephonySMSPlatform();
    TelephonySMSPlatform.instance = fakePlatform;

    expect(telephonySMSPlugin.requestPermission(), completes);
  });

  test('sendSMS', () async {
    TelephonySMS telephonySMSPlugin = TelephonySMS();
    MockTelephonySMSPlatform fakePlatform = MockTelephonySMSPlatform();
    TelephonySMSPlatform.instance = fakePlatform;

    expect(
      telephonySMSPlugin.sendSMS(phone: 'PHONE', message: 'MESSAGE'),
      completes,
    );
  });
}
