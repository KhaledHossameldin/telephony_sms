# telephony_sms

<?code-excerpt path-base="example/lib"?>

A Flutter plugin that allows you to send SMS messages in the background.

Note that his plugin works only supports Android as it uses Telephony which is a package provided by Android

|             | Android |
| ----------- | ------- |
| **Support** | SDK 16+ |

## Setup

To use this plugin you need to add `SEND_SMS` permission in the `AndroidManifest.xml` file. There are `debug`, `main` and `profile` versions which are chosen depending on how you start your app. In general, it's sufficient to add permission only to the `main` version.

```xml
    <uses-permission android:name="android.permission.SEND_SMS" />
    <application>
    ...
```

## How to use

First you need to request for the previously added `SEND_SMS` permission.

```dart
final _telephonySMS = TelephonySMS();

await _telephonySMS.requestPermission();
```

Now you can send any SMS message to any phone number desired.

```dart
await _telephonySMS.sendSMS(phone: "PHONE", message: "MESSAGE");
```

It is just as simple as that.
