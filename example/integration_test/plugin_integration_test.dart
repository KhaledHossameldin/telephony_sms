// This is a basic Flutter integration test.
//
// Since integration tests run in a full Flutter application, they can interact
// with the host side of a plugin implementation, unlike Dart unit tests.
//
// For more information about Flutter integration tests, please see
// https://docs.flutter.dev/cookbook/testing/integration/introduction

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:telephony_sms/telephony_sms.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('requestPermission resolves to a bool', (tester) async {
    final plugin = TelephonySMS();

    // Whether it resolves true or false depends on the device's current grant
    // state; what matters is that it resolves at all rather than hanging.
    await expectLater(plugin.requestPermission(), completion(isA<bool>()));
  });

  testWidgets('sendSMS rejects an empty phone number', (tester) async {
    final plugin = TelephonySMS();

    await expectLater(
      plugin.sendSMS(phone: '', message: 'hello'),
      throwsA(
        isA<TelephonySmsException>().having(
          (e) => e.code,
          'code',
          TelephonySmsErrorCode.invalidArguments,
        ),
      ),
    );
  });
}
