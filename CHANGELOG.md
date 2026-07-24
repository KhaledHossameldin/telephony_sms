## 0.1.0

### Breaking

* `requestPermission()` now returns `Future<bool>` instead of `Future<void>`. It
  resolves to whether the `SEND_SMS` permission is granted.
* Platform failures now throw a typed `TelephonySmsException` carrying a
  `TelephonySmsErrorCode`, instead of a raw `PlatformException`.
* The minimum supported Android API level is now 24 (was 19), following
  Flutter's own minimum.

Migration:

```dart
// Before
await telephonySMS.requestPermission();
await telephonySMS.sendSMS(phone: phone, message: message);

// After
final granted = await telephonySMS.requestPermission();
if (!granted) return;

try {
  await telephonySMS.sendSMS(phone: phone, message: message);
} on TelephonySmsException catch (e) {
  debugPrint('${e.code.name}: ${e.message}');
}
```

### Fixed

* `sendSMS()` never replied on the platform channel, so the returned future
  never completed. It now completes on success and throws on failure.
* `requestPermission()` never replied when the permission dialog was actually
  shown, so the first-run path hung forever. The pending result is now held and
  resolved from `onRequestPermissionsResult`.
* `sendSMS()` continued executing after reporting a missing `SmsManager`,
  dereferenced null, and then replied a second time — crashing the engine with
  "Reply already submitted".
* `SmsManager` was resolved behind an API 23 version gate, but
  `Context.getSystemService(SmsManager::class.java)` only returns an instance
  from API 31. On Android 6 through 11 it returned null without throwing, so the
  fallback to `SmsManager.getDefault()` was unreachable and sending always
  failed. The gate is now API 31 and the manager is resolved per send.
* A user who denied the permission once could never be prompted again: the
  `shouldShowRequestPermissionRationale` branch returned an error instead of
  re-requesting. Rationale handling is now left to the host app, and a denial
  simply resolves to `false`.
* Arguments are read with `MethodCall.argument` and validated, replacing an
  unchecked cast plus non-null assertions that threw on malformed input.
* The permission result listener was registered on every activity attach and
  never removed, leaking and double-registering across configuration changes.
* The application context is now cleared when the plugin detaches from the
  engine.

### Changed

* Android toolchain modernized: AGP 9.3.1, Kotlin 2.4.10, Gradle 9.6.1, Java 17,
  `compileSdk` 36, `minSdk` 24. Gradle build scripts migrated to Kotlin DSL.
* `plugin_platform_interface` raised to ^2.1.8 and `flutter_lints` to ^6.0.0.
* Unit tests replaced. The previous Kotlin test asserted a `getPlatformVersion`
  method that never existed, and the example's widget test asserted a widget the
  example never rendered.
* The example app now takes a phone number and message as input and reports
  results, instead of sending to the literal string `"PHONE"`.

### Known limitations

* Messages longer than one SMS part (160 GSM-7 characters, or 70 when any
  character falls outside GSM-7) are truncated. Multipart is not implemented.
* `sendSMS()` completes when the radio accepts the message, not when it is
  delivered.
* Only the default SIM subscription is used.

## 0.0.4

* Documentation and packaging touch-ups.

## 0.0.3

* Minor fixes.

## 0.0.2

* Expanded plugin metadata and README.

## 0.0.1

* Initial release: `requestPermission()` and `sendSMS()` over a method channel,
  Android only.
