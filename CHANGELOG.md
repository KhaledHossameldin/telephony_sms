## 0.1.0

### Breaking

* `requestPermission()` now returns `Future<bool>` instead of `Future<void>`. It
  resolves to whether the `SEND_SMS` permission is granted.
* Platform failures now throw a typed `TelephonySmsException` carrying a
  `TelephonySmsErrorCode`, instead of a raw `PlatformException`.
* `sendSMS()` now completes when the **radio confirms the message was sent**,
  rather than the moment it is handed off, and throws when the radio reports a
  failure. Previously a message that never left the device still reported
  success.
* The minimum supported Android API level is now 24 (was 19), following
  Flutter's own minimum.
* The minimum Flutter version is now 3.35.0. The Android build targets
  `compileSdk` 36 with Java 17 and the Kotlin 2.0 Gradle DSL, which is the first
  stable release whose own defaults meet all three. The previous constraint of
  `>=3.3.0` let pub resolve the package for projects that could then only fail
  inside Gradle.
* The method channel is now `com.khaledhossameldin/telephony_sms` rather than the
  unqualified `telephony_sms`. This is internal — it only matters if you were
  mocking the channel by name in tests.

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

### Added

* Send results are reported back from the radio. `sendSMS()` now passes a
  `sentIntent` and surfaces `noService`, `radioOff`, `nullPdu` and
  `genericFailure`. `sendTextMessage` never throws on radio failure — it reports
  only through that intent — so these failures were previously invisible and a
  dropped message looked identical to a delivered one. Resolves
  [#8](https://github.com/KhaledHossameldin/telephony_sms/issues/8).
* Optional delivery confirmation via `completeOn: SmsCompletion.delivered`,
  documented as carrier-dependent. Resolves
  [#7](https://github.com/KhaledHossameldin/telephony_sms/issues/7).
* Multipart messages. Long messages are split with `divideMessage` and sent via
  `sendMultipartTextMessage`, succeeding only if every part does. Messages over
  160 GSM-7 characters, or 70 containing Arabic or emoji, were previously
  truncated without warning.
* A `timeout` on `sendSMS()`, defaulting to 30 seconds, so a confirmation that
  never arrives cannot hang the future.
* The full Android result-code table. Android defines around eighty send result
  codes and only four were mapped, so everything else surfaced as
  `sendFailed: unknown result code N`. `limitExceeded` matters most — Android
  caps how many messages an app may send in a window, which is the usual failure
  when sending in a loop. Also `simAbsent`, `simError`, `noDefaultSmsApp`,
  `userNotAllowed`, `fdnCheckFailure`, `shortCodeNotAllowed`,
  `shortCodeNeverAllowed`, `radioNotAvailable`, `networkReject`, `networkError`,
  `modemError`, `noMemory`, `noResources`, `systemError`, `invalidSmsFormat`,
  `encodingError`, `invalidSmscAddress`, `operationNotAllowed`,
  `blockedDuringEmergency` and `cancelled`.
* `TelephonySmsErrorCode.isTransient`, which says whether retrying the same send
  could plausibly succeed later, so callers no longer have to hand-roll the list.
* `permissionNotDeclared`, raised when the host app's manifest is missing
  `<uses-permission android:name="android.permission.SEND_SMS" />`. The
  permission can never be granted without it, and every request used to come back
  as a plain `false`, indistinguishable from the user tapping Deny.
* `deliveryTimeout`, separate from `sendTimeout`. Both used to be `sendTimeout`,
  which conflated "never sent, retry" with "sent but unconfirmed, retrying sends
  it twice".
* `noTelephony`, for tablets and other Wi-Fi-only devices. They hand out an
  `SmsManager` and fail only once the message reaches it.
* `noSmsSubscription`, raised up front on a multi-SIM device with no default SMS
  subscription. See the security fixes below for why.
* `receiverUnavailable`, raised when the plugin cannot register its result
  receiver. See the security fixes below.
* `unsupportedPlatform`, for calls on platforms other than Android.
* `==` and `hashCode` on `TelephonySmsException`, and a `const` constructor on
  `TelephonySMS`.

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
* Delivery confirmation was read from the broadcast's result code, which the
  framework sets to `RESULT_OK` on every delivery broadcast whatever the carrier
  reported. The real outcome is in the status report's PDU, so
  `SmsCompletion.delivered` reported failed deliveries as successes and
  `notDelivered` was unreachable. The PDU is now parsed and its status
  classified, including the CDMA encoding, where the value is shifted into the
  upper bits and a permanent failure has a zero low octet — exactly the shape a
  GSM reader mistakes for "delivered".
* A multipart send waited for every part to report before surfacing a failure.
  When one part fails the radio usually abandons the rest, so their broadcasts
  never arrive and the caller got `sendTimeout` instead of the real cause. A
  failure is now reported as soon as any part reports one.
* Two Flutter engines in one process cross-talked. Send ids and `PendingIntent`
  request codes were per-instance and both started at zero, while the broadcast
  actions are per-package, so each engine could resolve the other's future and
  colliding request codes let `FLAG_UPDATE_CURRENT` rewrite the other's extras.
  Ids now carry a per-instance token and request codes are process-wide.
* A permission request in flight when the plugin detached from the engine was
  left hanging forever. Only activity detach was handled.
* `onRequestPermissionsResult` matched on the request code alone, so another
  plugin using the same code resolved this plugin's pending result with its grant
  array. It now confirms the result is for `SEND_SMS`, and reads the grant by
  index rather than assuming the first slot. An interrupted request, which comes
  back with both arrays empty, is treated as a denial rather than hanging.
* `SecurityException`, `IllegalArgumentException` and `UnsupportedOperationException`
  from `SmsManager` all collapsed into `sendFailed`. They now map to
  `permissionDenied` (revoked mid-send), `invalidArguments` (bad destination
  address) and `noTelephony`.
* `MissingPluginException` escaped untyped on every platform but Android,
  breaking the promise that failures arrive as a `TelephonySmsException`.
* A message needing more than 255 parts is now rejected with `invalidArguments`
  naming the count, rather than failing opaquely inside the platform.
* The example app's manifest now marks telephony hardware optional. Without it,
  `SEND_SMS` makes Google Play hide the app from every tablet and Wi-Fi-only
  device.

### Security

A dedicated audit found no exploitable vulnerability, but three paths to the same
costly outcome — the plugin losing track of a send, reporting a retryable
`sendTimeout`, and the caller resending a message that had in fact gone out, at the
user's expense. `SECURITY.md` classes "sent more than once per call" as a security
issue, so these are called out separately.

* Sends are now bound to an explicit SMS subscription with `createForSubscriptionId`
  rather than the default-subscription `SmsManager`. On a multi-SIM device with no
  default SMS subscription, the default manager pops a system SIM-picker in the
  foreground and fails silently in the background — AOSP explicitly warns against
  using it off the main path. Both cases previously ended in `sendTimeout` while
  the message was still queued, so a retry duplicated it. The plugin now refuses
  such a send up front with `noSmsSubscription`.
* `sendTimeout` is no longer `isTransient`. It means the outcome is *unknown*, and
  an automatic retry duplicates the message whenever it did send. The timeout path
  also no longer cancels the sent `PendingIntent`, which had made a late radio
  report surface as a `CanceledException` instead of firing harmlessly.
* A failed result-receiver registration no longer leaves the plugin half-alive.
  Below API 33 `ContextCompat.registerReceiver` throws when the app's merged
  manifest lacks the signature permission androidx.core contributes, and a forced
  androidx.core below 1.9.0 throws `NoSuchMethodError`; either was swallowed by the
  plugin registrant, leaving the channel live but no receiver and no activity
  binding, so every send timed out. Registration failure is now caught and every
  send refused with `receiverUnavailable` rather than sent blind.
* `hasTelephonyMessaging` accepted only `FEATURE_TELEPHONY_MESSAGING`, while the
  plugin's own setup docs and example manifest declare `FEATURE_TELEPHONY`. A
  device declaring only the parent feature was refused with `noTelephony` on
  hardware that could send. Either feature is now accepted.
* CI now runs with an explicit least-privilege `permissions: contents: read`, and
  the third-party actions are pinned to commit SHAs rather than mutable tags.

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

* The default SMS subscription is used, and a dual-SIM device must have one set —
  otherwise the send is refused with `noSmsSubscription`. There is no API to pick a
  specific SIM per send.
* Delivery reports are only as reliable as the carrier. `SmsCompletion.delivered`
  ends in `deliveryTimeout` on networks that do not emit them, which is many of
  them.
* A send does not survive the process. Results arrive on a runtime-registered
  receiver, so a send in flight when Android kills the app is never reported,
  even though the message may still go out.

## 0.0.4

* Documentation and packaging touch-ups.

## 0.0.3

* Minor fixes.

## 0.0.2

* Expanded plugin metadata and README.

## 0.0.1

* Initial release: `requestPermission()` and `sendSMS()` over a method channel,
  Android only.
