import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:telephony_sms/telephony_sms_completion.dart';
import 'package:telephony_sms/telephony_sms_exception.dart';
import 'package:telephony_sms/telephony_sms_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final platform = MethodChannelTelephonySMS();
  const channel = MethodChannel('telephony_sms');

  final log = <MethodCall>[];

  /// Installs a handler that records every call and replies with [response],
  /// or throws [error] instead when one is supplied.
  void mockHandler({Object? response, PlatformException? error}) {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (methodCall) async {
      log.add(methodCall);
      if (error != null) throw error;
      return response;
    });
  }

  setUp(log.clear);

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  group('requestPermission', () {
    test('invokes requestPermission and returns the granted flag', () async {
      mockHandler(response: true);

      await expectLater(platform.requestPermission(), completion(isTrue));
      expect(log, hasLength(1));
      expect(log.single.method, 'requestPermission');
    });

    test('returns false when the platform denies', () async {
      mockHandler(response: false);

      await expectLater(platform.requestPermission(), completion(isFalse));
    });

    test('returns false when the platform replies with null', () async {
      mockHandler(response: null);

      await expectLater(platform.requestPermission(), completion(isFalse));
    });

    test('maps a PlatformException onto TelephonySmsException', () async {
      mockHandler(
        error: PlatformException(code: 'no_activity', message: 'No activity.'),
      );

      await expectLater(
        platform.requestPermission(),
        throwsA(
          isA<TelephonySmsException>()
              .having((e) => e.code, 'code', TelephonySmsErrorCode.noActivity)
              .having((e) => e.message, 'message', 'No activity.'),
        ),
      );
    });
  });

  group('sendSMS', () {
    test('invokes sendSMS with phone, message, completeOn and timeout',
        () async {
      mockHandler();

      await platform.sendSMS(phone: '+201000000000', message: 'hello');

      expect(log, hasLength(1));
      expect(log.single.method, 'sendSMS');
      expect(log.single.arguments, {
        'phone': '+201000000000',
        'message': 'hello',
        'completeOn': 'sent',
        'timeoutMillis': 30000,
      });
    });

    test('sends the delivered completion and a custom timeout', () async {
      mockHandler();

      await platform.sendSMS(
        phone: 'PHONE',
        message: 'MESSAGE',
        completeOn: SmsCompletion.delivered,
        timeout: const Duration(minutes: 2),
      );

      expect(log.single.arguments, {
        'phone': 'PHONE',
        'message': 'MESSAGE',
        'completeOn': 'delivered',
        'timeoutMillis': 120000,
      });
    });

    test('maps a PlatformException onto TelephonySmsException', () async {
      mockHandler(
        error: PlatformException(
          code: 'permission_denied',
          message: 'Not granted.',
        ),
      );

      await expectLater(
        platform.sendSMS(phone: 'PHONE', message: 'MESSAGE'),
        throwsA(
          isA<TelephonySmsException>().having(
            (e) => e.code,
            'code',
            TelephonySmsErrorCode.permissionDenied,
          ),
        ),
      );
    });

    test('maps an unrecognised platform code onto unknown', () async {
      mockHandler(
        error: PlatformException(code: 'something_new', message: 'Nope.'),
      );

      await expectLater(
        platform.sendSMS(phone: 'PHONE', message: 'MESSAGE'),
        throwsA(
          isA<TelephonySmsException>().having(
            (e) => e.code,
            'code',
            TelephonySmsErrorCode.unknown,
          ),
        ),
      );
    });

    // These are the codes that only became reachable once the send result was
    // actually reported back from the radio.
    for (final entry in const {
      'no_service': TelephonySmsErrorCode.noService,
      'radio_off': TelephonySmsErrorCode.radioOff,
      'null_pdu': TelephonySmsErrorCode.nullPdu,
      'generic_failure': TelephonySmsErrorCode.genericFailure,
      'send_timeout': TelephonySmsErrorCode.sendTimeout,
      'not_delivered': TelephonySmsErrorCode.notDelivered,
      'cancelled': TelephonySmsErrorCode.cancelled,
    }.entries) {
      test('maps ${entry.key} onto ${entry.value.name}', () async {
        mockHandler(
          error: PlatformException(code: entry.key, message: 'Failed.'),
        );

        await expectLater(
          platform.sendSMS(phone: 'PHONE', message: 'MESSAGE'),
          throwsA(
            isA<TelephonySmsException>()
                .having((e) => e.code, 'code', entry.value),
          ),
        );
      });
    }
  });
}
