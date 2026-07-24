/// What counts as completion for a send.
enum SmsCompletion {
  /// Complete once the radio confirms the message was sent.
  ///
  /// This is the default, and the one you want for a retry loop: a failure here
  /// means the message never left the device.
  sent('sent'),

  /// Complete once the carrier confirms the message reached the recipient.
  ///
  /// Delivery reports are optional on the network side. Plenty of carriers, and
  /// most emulators, never emit one — in that case the send fails with
  /// `TelephonySmsErrorCode.sendTimeout` even though the message went out. Only
  /// opt in when your carrier is known to support them, and keep the timeout
  /// generous.
  delivered('delivered');

  const SmsCompletion(this.value);

  /// The raw string used on the method channel.
  final String value;
}
