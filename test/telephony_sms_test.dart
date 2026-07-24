import 'package:flutter_test/flutter_test.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:telephony_sms/telephony_sms.dart';
import 'package:telephony_sms/telephony_sms_method_channel.dart';
import 'package:telephony_sms/telephony_sms_platform_interface.dart';

class MockTelephonySMSPlatform
    with MockPlatformInterfaceMixin
    implements TelephonySMSPlatform {
  MockTelephonySMSPlatform({this.granted = true, this.failure});

  final bool granted;
  final TelephonySmsException? failure;

  String? sentPhone;
  String? sentMessage;
  SmsCompletion? sentCompleteOn;
  Duration? sentTimeout;

  @override
  Future<bool> requestPermission() async {
    final failure = this.failure;
    if (failure != null) throw failure;
    return granted;
  }

  @override
  Future<void> sendSMS({
    required String phone,
    required String message,
    SmsCompletion completeOn = SmsCompletion.sent,
    Duration timeout = const Duration(seconds: 30),
  }) async {
    final failure = this.failure;
    if (failure != null) throw failure;
    sentPhone = phone;
    sentMessage = message;
    sentCompleteOn = completeOn;
    sentTimeout = timeout;
  }
}

void main() {
  final initialPlatform = TelephonySMSPlatform.instance;

  tearDown(() => TelephonySMSPlatform.instance = initialPlatform);

  test('$MethodChannelTelephonySMS is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelTelephonySMS>());
  });

  group('requestPermission', () {
    test('propagates a granted result', () async {
      TelephonySMSPlatform.instance = MockTelephonySMSPlatform(granted: true);

      await expectLater(TelephonySMS().requestPermission(), completion(isTrue));
    });

    test('propagates a denied result', () async {
      TelephonySMSPlatform.instance = MockTelephonySMSPlatform(granted: false);

      await expectLater(
        TelephonySMS().requestPermission(),
        completion(isFalse),
      );
    });

    test('rethrows a TelephonySmsException', () async {
      TelephonySMSPlatform.instance = MockTelephonySMSPlatform(
        failure: const TelephonySmsException(
          TelephonySmsErrorCode.noActivity,
          'No activity.',
        ),
      );

      await expectLater(
        TelephonySMS().requestPermission(),
        throwsA(isA<TelephonySmsException>()),
      );
    });
  });

  group('sendSMS', () {
    test('forwards phone and message to the platform', () async {
      final fakePlatform = MockTelephonySMSPlatform();
      TelephonySMSPlatform.instance = fakePlatform;

      await TelephonySMS().sendSMS(phone: '+201000000000', message: 'hello');

      expect(fakePlatform.sentPhone, '+201000000000');
      expect(fakePlatform.sentMessage, 'hello');
    });

    test('defaults to completing on sent, with a 30 second timeout', () async {
      final fakePlatform = MockTelephonySMSPlatform();
      TelephonySMSPlatform.instance = fakePlatform;

      await TelephonySMS().sendSMS(phone: 'PHONE', message: 'MESSAGE');

      expect(fakePlatform.sentCompleteOn, SmsCompletion.sent);
      expect(fakePlatform.sentTimeout, const Duration(seconds: 30));
    });

    test('forwards an explicit completeOn and timeout', () async {
      final fakePlatform = MockTelephonySMSPlatform();
      TelephonySMSPlatform.instance = fakePlatform;

      await TelephonySMS().sendSMS(
        phone: 'PHONE',
        message: 'MESSAGE',
        completeOn: SmsCompletion.delivered,
        timeout: const Duration(minutes: 2),
      );

      expect(fakePlatform.sentCompleteOn, SmsCompletion.delivered);
      expect(fakePlatform.sentTimeout, const Duration(minutes: 2));
    });

    test('rethrows a TelephonySmsException', () async {
      TelephonySMSPlatform.instance = MockTelephonySMSPlatform(
        failure: const TelephonySmsException(
          TelephonySmsErrorCode.permissionDenied,
          'Not granted.',
        ),
      );

      await expectLater(
        TelephonySMS().sendSMS(phone: 'PHONE', message: 'MESSAGE'),
        throwsA(
          isA<TelephonySmsException>().having(
            (e) => e.code,
            'code',
            TelephonySmsErrorCode.permissionDenied,
          ),
        ),
      );
    });
  });

  group('TelephonySmsErrorCode.fromCode', () {
    test('maps every known wire code', () {
      for (final value in TelephonySmsErrorCode.values) {
        expect(TelephonySmsErrorCode.fromCode(value.code), value);
      }
    });

    // Two values sharing a wire string would make fromCode return whichever
    // comes first, silently discarding the other.
    test('every wire code is unique', () {
      final codes = TelephonySmsErrorCode.values.map((e) => e.code).toList();

      expect(codes.toSet(), hasLength(codes.length));
    });

    test('falls back to unknown', () {
      expect(
        TelephonySmsErrorCode.fromCode('not_a_real_code'),
        TelephonySmsErrorCode.unknown,
      );
      expect(
        TelephonySmsErrorCode.fromCode(null),
        TelephonySmsErrorCode.unknown,
      );
    });
  });

  group('TelephonySmsErrorCode.isTransient', () {
    test('covers the conditions that clear on their own', () {
      const transient = [
        TelephonySmsErrorCode.noService,
        TelephonySmsErrorCode.radioOff,
        TelephonySmsErrorCode.radioNotAvailable,
        TelephonySmsErrorCode.genericFailure,
        TelephonySmsErrorCode.limitExceeded,
        TelephonySmsErrorCode.networkReject,
        TelephonySmsErrorCode.networkError,
        TelephonySmsErrorCode.modemError,
        TelephonySmsErrorCode.noMemory,
        TelephonySmsErrorCode.noResources,
        TelephonySmsErrorCode.systemError,
      ];

      for (final code in TelephonySmsErrorCode.values) {
        expect(
          code.isTransient,
          transient.contains(code),
          reason: '${code.name} is on the wrong side of isTransient',
        );
      }
    });

    // The key change: an unknown outcome must not auto-retry, because a retry
    // duplicates the message on every case where it actually went out.
    test('excludes the timeouts, where a retry risks a paid duplicate', () {
      expect(TelephonySmsErrorCode.sendTimeout.isTransient, isFalse);
      expect(TelephonySmsErrorCode.deliveryTimeout.isTransient, isFalse);
      expect(TelephonySmsErrorCode.notDelivered.isTransient, isFalse);
    });

    test('excludes anything the user or the app has to fix first', () {
      expect(TelephonySmsErrorCode.permissionDenied.isTransient, isFalse);
      expect(TelephonySmsErrorCode.permissionNotDeclared.isTransient, isFalse);
      expect(TelephonySmsErrorCode.noSmsSubscription.isTransient, isFalse);
      expect(TelephonySmsErrorCode.receiverUnavailable.isTransient, isFalse);
      expect(TelephonySmsErrorCode.fdnCheckFailure.isTransient, isFalse);
      expect(TelephonySmsErrorCode.shortCodeNeverAllowed.isTransient, isFalse);
      expect(TelephonySmsErrorCode.unsupportedPlatform.isTransient, isFalse);
    });
  });

  group('TelephonySmsException', () {
    test('compares by code and message', () {
      const a = TelephonySmsException(
        TelephonySmsErrorCode.radioOff,
        'Radio off.',
      );
      const b = TelephonySmsException(
        TelephonySmsErrorCode.radioOff,
        'Radio off.',
      );
      const differentMessage = TelephonySmsException(
        TelephonySmsErrorCode.radioOff,
        'Something else.',
      );
      const differentCode = TelephonySmsException(
        TelephonySmsErrorCode.noService,
        'Radio off.',
      );

      expect(a, b);
      expect(a.hashCode, b.hashCode);
      expect(a, isNot(differentMessage));
      expect(a, isNot(differentCode));
    });

    test('describes itself with the wire code', () {
      const exception = TelephonySmsException(
        TelephonySmsErrorCode.limitExceeded,
        'Too many.',
      );

      expect(
        exception.toString(),
        'TelephonySmsException(limit_exceeded): Too many.',
      );
    });
  });
}
