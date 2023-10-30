import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:telephony_sms/telephony_sms_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelTelephonySMS platform = MethodChannelTelephonySMS();
  const MethodChannel channel = MethodChannel('telephony_sms');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('requestPermission', () async {
    expect(platform.requestPermission(), completes);
  });

  test('sendSMS', () async {
    expect(platform.sendSMS(phone: 'PHONE', message: 'MESSAGE'), completes);
  });
}
