# telephony_sms

[![pub package](https://img.shields.io/pub/v/telephony_sms.svg)](https://pub.dev/packages/telephony_sms)
[![CI](https://github.com/KhaledHossameldin/telephony_sms/actions/workflows/ci.yml/badge.svg)](https://github.com/KhaledHossameldin/telephony_sms/actions/workflows/ci.yml)
[![License: BSD-3-Clause](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)

A Flutter plugin that sends SMS messages in the background, without opening the
system messaging app.

Android only. It wraps Android's `SmsManager`, which has no iOS equivalent — iOS
does not permit sending SMS without user interaction.

|             | Android         |
| ----------- | --------------- |
| **Support** | SDK 24+         |
| **Flutter** | 3.35 or newer   |

Messages are sent silently in the background, split across multiple parts when
needed, and the send result is reported back so failures are visible rather than
silent.

## Setup

Add the `SEND_SMS` permission to your app's `AndroidManifest.xml`. Android
projects have `debug`, `main`, and `profile` variants; declaring it in `main` is
enough, since that manifest is merged into every build type.

```xml
    <uses-permission android:name="android.permission.SEND_SMS" />
    <uses-feature android:name="android.hardware.telephony" android:required="false" />
    <application>
    ...
```

The `uses-feature` line matters for distribution. `SEND_SMS` implies that your
app requires telephony hardware, and Google Play then hides it from every tablet
and Wi-Fi-only device. Marking the feature optional keeps those users, and
`sendSMS()` reports `noTelephony` on a device that cannot send.

The plugin deliberately does **not** declare `SEND_SMS` in its own manifest, so
that adding the dependency never silently adds a restricted permission to your
app. If you forget the line, `requestPermission()` throws
`permissionNotDeclared` rather than quietly returning `false` forever.

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

The outcome is read out of the carrier's status report itself, so a report that
says the message could not be delivered throws `notDelivered` rather than
resolving successfully. A report that says the network is still trying is not an
answer either way, and the call keeps waiting.

If nothing conclusive arrives before `timeout`, the call throws
`deliveryTimeout` — a separate code from `sendTimeout` precisely because the
message **did** go out. Never retry on it. Treat delivery as a bonus, not a
guarantee.

### Retrying

`isTransient` tells you whether retrying is worthwhile:

```dart
try {
  await telephonySMS.sendSMS(phone: phone, message: message);
} on TelephonySmsException catch (e) {
  if (e.code.isTransient) {
    // No service, radio off, rate limited, busy network — retry with backoff.
  } else {
    // Nothing will change on its own. Surface it.
    rethrow;
  }
}
```

`limitExceeded` deserves a mention: Android caps how many messages an app may
send in a window, so it is the usual failure when sending in a loop. It is
transient, but retrying immediately just hits the cap again.

### Error handling

Every platform failure throws a `TelephonySmsException` with a
`TelephonySmsErrorCode`. Android defines around eighty send result codes; they
are grouped here by what you can do about them, so several map onto one value.

Raised before anything reaches the radio:

| Code                     | Meaning                                                          |
| ------------------------ | ---------------------------------------------------------------- |
| `invalidArguments`       | `phone` or `message` was empty, blank, or rejected by the platform |
| `permissionDenied`       | `SEND_SMS` is not granted — call `requestPermission()` first      |
| `permissionNotDeclared`  | Your manifest is missing `<uses-permission>`, so it can never be granted |
| `noActivity`             | No activity attached, so the permission dialog cannot be shown    |
| `activityDetached`       | The activity went away before the permission dialog was answered  |
| `alreadyRequesting`      | A permission request is already in flight                        |
| `smsManagerUnavailable`  | The device provides no `SmsManager`                              |
| `noTelephony`            | The device cannot send SMS — a tablet or Wi-Fi-only device        |
| `noSmsSubscription`      | No default SMS SIM is set on a multi-SIM device (see Limitations)  |
| `receiverUnavailable`    | The plugin could not register its result receiver (see Limitations) |
| `sendFailed`             | The message was rejected before reaching the radio               |
| `noContext`              | The plugin is not attached to a Flutter engine                   |
| `unsupportedPlatform`    | Not Android                                                      |

Reported by the radio. The **Retry** column is what `isTransient` returns:

| Code                       | Meaning                                                | Retry |
| -------------------------- | ------------------------------------------------------ | ----- |
| `noService`                | No cellular service                                    | yes   |
| `radioOff`                 | The radio is off, most likely airplane mode            | yes   |
| `radioNotAvailable`        | The radio is unavailable                               | yes   |
| `genericFailure`           | Unspecified radio failure                              | yes   |
| `limitExceeded`            | The device's SMS rate limit was reached                | yes, with backoff |
| `networkReject`            | The network rejected the message                       | yes   |
| `networkError`             | The network could not carry the message                | yes   |
| `modemError`               | The modem reported an error                            | yes   |
| `noMemory`                 | The device is out of memory                            | yes   |
| `noResources`              | The telephony stack is out of resources                | yes   |
| `systemError`              | An internal telephony error                            | yes   |
| `nullPdu`                  | The telephony stack produced a null PDU                | no    |
| `fdnCheckFailure`          | The number is not in the SIM's fixed dialling list     | no    |
| `shortCodeNotAllowed`      | Sending to this short code was not permitted           | no    |
| `shortCodeNeverAllowed`    | Sending to this short code is never permitted          | no    |
| `invalidSmsFormat`         | The message format is invalid                          | no    |
| `encodingError`            | The message could not be encoded                       | no    |
| `invalidSmscAddress`       | The SMS centre address is invalid                      | no    |
| `operationNotAllowed`      | Not allowed on this device                             | no    |
| `blockedDuringEmergency`   | Blocked while an emergency call is in progress         | no    |
| `noDefaultSmsApp`          | No SMS subscription could be resolved — usually an unset SMS SIM | no |
| `userNotAllowed`           | This user profile may not send SMS                     | no    |
| `simAbsent`                | There is no SIM                                        | no    |
| `simError`                 | The SIM is locked, busy, full, or in an invalid state  | no    |
| `cancelled`                | The platform cancelled the send                        | no    |

The state of the send afterwards:

| Code               | Meaning                                                       | Retry |
| ------------------ | ------------------------------------------------------------- | ----- |
| `sendTimeout`      | Outcome unknown — no confirmation arrived before `timeout`     | **no**, see below |
| `deliveryTimeout`  | Sent, but no delivery report arrived before `timeout`         | **no** |
| `notDelivered`     | Sent, but the carrier reported it as not delivered            | no    |
| `detached`         | The plugin detached while the call was in flight              | —     |
| `unknown`          | An error code this version of the plugin does not recognise   | no    |

`sendTimeout` means the plugin **could not tell** whether the message went out —
it may have. It is deliberately not retryable: an automatic retry would duplicate
the message whenever it did send. Decide per message whether a possible duplicate
is acceptable before retrying by hand.

Note that a denied permission is **not** an exception — `requestPermission()`
returns `false`. Exceptions are reserved for calls that could not be made.

## Google Play policy

`SEND_SMS` is a [restricted permission][play-sms]. Apps requesting it on Google
Play must qualify for an eligible use case and submit a permissions declaration,
or they will be rejected. Check that your app qualifies before shipping.

It is also *hard restricted*: on a build installed from Play without an approved
declaration, the permission is not grantable at all. The symptom is
`requestPermission()` returning `false` with **no dialog ever appearing** — which
looks exactly like a user denial but is not one. If you see that on a Play build,
the declaration, not the user, is the problem.

[play-sms]: https://support.google.com/googleplay/android-developer/answer/10208820

## Limitations

* **A default SMS SIM must be set on dual-SIM devices.** The plugin sends on the
  default SMS subscription. If none is set — SMS left on "Ask every time" — it
  fails fast with `noSmsSubscription` rather than sending, because the platform
  would otherwise pop a system SIM-chooser dialog in the foreground, or fail
  silently in the background. There is no API to pick a specific SIM per send.
* **Delivery reports depend on the carrier.** `SmsCompletion.delivered` can only
  report what the network tells it, and many networks tell it nothing.
* **A send does not survive the process.** Results arrive on a runtime-registered
  receiver, so if Android kills the app mid-send the outcome is never reported.
  The message may still go out.
* **The result receiver depends on androidx.core ≥ 1.9.0.** It normally arrives
  transitively and is declared explicitly, but if your build forces an older
  androidx.core, or its merged manifest strips the signature permission
  androidx.core contributes, the plugin cannot confirm sends and refuses them with
  `receiverUnavailable` rather than sending blind.

## Example

See [`example/`](example) for a runnable app that requests the permission and
sends a message to a number you type in.
