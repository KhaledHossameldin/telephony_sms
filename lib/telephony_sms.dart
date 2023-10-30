import 'telephony_sms_platform_interface.dart';

class TelephonySMS {
  Future<void> requestPermission() {
    return TelephonySMSPlatform.instance.requestPermission();
  }

  Future<void> sendSMS({
    required String phone,
    required String message,
  }) {
    return TelephonySMSPlatform.instance.sendSMS(
      phone: phone,
      message: message,
    );
  }
}
