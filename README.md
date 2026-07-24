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

`sendSMS()` completes once the message has been handed to the radio.

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
| `sendFailed`             | `SmsManager` rejected the message                              |
| `noContext`              | The plugin is not attached to a Flutter engine                 |
| `unknown`                | An error code this version of the plugin does not recognise    |

Note that a denied permission is **not** an exception — `requestPermission()`
returns `false`. Exceptions are reserved for calls that could not be made.

## Google Play policy

`SEND_SMS` is a [restricted permission][play-sms]. Apps requesting it on Google
Play must qualify for an eligible use case and submit a permissions declaration,
or they will be rejected. Check that your app qualifies before shipping.

[play-sms]: https://support.google.com/googleplay/android-developer/answer/10208820

## Limitations

* **Single-part messages only.** Messages longer than one SMS part — 160
  characters in GSM-7, or 70 when any character falls outside it, which includes
  all Arabic text and emoji — are truncated. Multipart is not implemented.
* **No delivery confirmation.** `sendSMS()` completes when the radio accepts the
  message, not when the recipient receives it.
* **Default SIM only.** There is no way to choose a subscription on dual-SIM
  devices.

## Example

See [`example/`](example) for a runnable app that requests the permission and
sends a message to a number you type in.
