# telephony_sms

[![pub package](https://img.shields.io/pub/v/telephony_sms.svg)](https://pub.dev/packages/telephony_sms)
[![CI](https://github.com/KhaledHossameldin/telephony_sms/actions/workflows/ci.yml/badge.svg)](https://github.com/KhaledHossameldin/telephony_sms/actions/workflows/ci.yml)
[![License: BSD-3-Clause](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)

A Flutter plugin that sends SMS messages in the background, without opening the
system messaging app.

Android only. It wraps Android's `SmsManager`, which has no iOS equivalent — iOS
does not permit sending SMS without user interaction.

|             | Android  |
| ----------- | -------- |
| **Support** | SDK 24+  |

Messages are sent silently in the background, split across multiple parts when
needed, and the send result is reported back so failures are visible rather than
silent.

## Setup

Add the `SEND_SMS` permission to your app's `AndroidManifest.xml`. Android
projects have `debug`, `main`, and `profile` variants; declaring it in `main` is
enough, since that manifest is merged into every build type.

```xml
    <uses-permission android:name="android.permission.SEND_SMS" />
    <application>
    ...
```

The plugin deliberately does **not** declare `SEND_SMS` in its own manifest, so
that adding the dependency never silently adds a restricted permission to your
app.

## Usage

Request the permission before sending anything. `requestPermission()` resolves
to whether the permission is granted — it returns `true` immediately if it was
already granted, and `false` if the user denies the dialog.

```dart
final telephonySMS = TelephonySMS();

final granted = await telephonySMS.requestPermission();
if (!granted) return;
```

Then send:

```dart
try {
  await telephonySMS.sendSMS(phone: '+201000000000', message: 'Hello!');
} on TelephonySmsException catch (e) {
  debugPrint('${e.code.name}: ${e.message}');
}
```

`sendSMS()` completes once **the radio confirms the message was sent**, and
throws otherwise. Long messages are split and sent as a multipart message; the
send succeeds only if every part does.

### Sent vs delivered

These are two different signals, and only the first is reliable.

**Sent** means the radio accepted the message and it left the device. This is
what `sendSMS()` waits for by default, and it is the one that matters for a retry
loop — a failure here means the message never went out.

**Delivered** means the carrier confirmed it reached the recipient's handset.
Delivery reports are optional on the network side, and plenty of carriers never
emit one. Opt in with `completeOn`:

```dart
await telephonySMS.sendSMS(
  phone: '+201000000000',
  message: 'Hello!',
  completeOn: SmsCompletion.delivered,
  timeout: const Duration(minutes: 2),
);
```

If no report arrives before `timeout`, the call throws `sendTimeout` even though
the message may well have arrived. Treat delivery as a bonus, not a guarantee.

### Retrying

The error code tells you whether retrying is worthwhile:

```dart
try {
  await telephonySMS.sendSMS(phone: phone, message: message);
} on TelephonySmsException catch (e) {
  switch (e.code) {
    case TelephonySmsErrorCode.noService:
    case TelephonySmsErrorCode.radioOff:
    case TelephonySmsErrorCode.genericFailure:
      // Transient — retry once conditions change.
    case TelephonySmsErrorCode.permissionDenied:
      // Not retryable until the user grants the permission.
    default:
      rethrow;
  }
}
```

### Error handling

Every platform failure throws a `TelephonySmsException` with a
`TelephonySmsErrorCode`:

| Code                     | Meaning                                                        |
| ------------------------ | -------------------------------------------------------------- |
| `invalidArguments`       | `phone` or `message` was empty or blank                        |
| `permissionDenied`       | `SEND_SMS` is not granted — call `requestPermission()` first    |
| `noActivity`             | No activity attached, so the permission dialog cannot be shown  |
| `activityDetached`       | The activity went away before the permission dialog was answered |
| `alreadyRequesting`      | A permission request is already in flight                      |
| `smsManagerUnavailable`  | The device provides no `SmsManager`                            |
| `sendFailed`             | The message was rejected before reaching the radio             |
| `noContext`              | The plugin is not attached to a Flutter engine                 |
| `noService`              | No cellular service. Retryable                                 |
| `radioOff`               | The radio is off, most likely airplane mode. Retryable         |
| `nullPdu`                | The telephony stack produced a null PDU                        |
| `genericFailure`         | Unspecified radio failure, often transient                     |
| `sendTimeout`            | No confirmation arrived before `timeout`                       |
| `notDelivered`           | Sent, but the carrier reported it as not delivered             |
| `cancelled`              | The plugin detached while the send was in flight               |
| `unknown`                | An error code this version of the plugin does not recognise    |

Note that a denied permission is **not** an exception — `requestPermission()`
returns `false`. Exceptions are reserved for calls that could not be made.

## Google Play policy

`SEND_SMS` is a [restricted permission][play-sms]. Apps requesting it on Google
Play must qualify for an eligible use case and submit a permissions declaration,
or they will be rejected. Check that your app qualifies before shipping.

[play-sms]: https://support.google.com/googleplay/android-developer/answer/10208820

## Limitations

* **Default SIM only.** There is no way to choose a subscription on dual-SIM
  devices.
* **Delivery reports depend on the carrier.** `SmsCompletion.delivered` can only
  report what the network tells it, and many networks tell it nothing.

## Example

See [`example/`](example) for a runnable app that requests the permission and
sends a message to a number you type in.
