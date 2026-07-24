# Contributing to telephony_sms

Thanks for taking the time to contribute.

## Getting set up

```bash
git clone https://github.com/KhaledHossameldin/telephony_sms.git
cd telephony_sms
flutter pub get
(cd example && flutter pub get)
```

You need the Flutter stable channel, a JDK 17 (the one bundled with Android
Studio works), and an Android SDK with API 36 installed.

## Branching and pull requests

`main` is the release branch. Day-to-day work lands on `dev` first.

1. Branch from `dev`.
2. Open your pull request against `dev`.
3. `dev` is merged into `main` when a release is cut.

CI must be green before a pull request is merged.

## Running the tests

There are three suites, and all three run in CI. Please run them locally before
opening a pull request.

Package unit tests:

```bash
flutter test
```

Example app widget tests:

```bash
cd example && flutter test
```

Plugin Kotlin unit tests. The Gradle wrapper is not checked in — the Flutter tool
writes it during an Android build — so build once first:

```bash
cd example && flutter build apk --debug && cd android && ./gradlew :telephony_sms:testDebugUnitTest
```

## Before you push

```bash
dart format .
```

```bash
flutter analyze && (cd example && flutter analyze)
```

The analyzer is configured strictly, including `public_member_api_docs`. Any new
public API needs a doc comment. `dart fix --apply` resolves most mechanical
violations.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/). Keep the subject
under 50 characters and explain *why* in the body when it is not obvious.

```
fix(android): reply exactly once when SmsManager is missing

The error path fell through to a null dereference, producing a second
reply and crashing the engine with "Reply already submitted".
```

## Changing the public API

Breaking changes need a major-or-minor version bump (this package is pre-1.0, so
minor), a `### Breaking` section in `CHANGELOG.md`, and a migration snippet
showing the before and after.

## Testing on a device

Sending SMS costs real money and cannot be meaningfully verified on a single
emulator. Two emulator instances can message each other by using the target
emulator's port number (for example `5556`) as the phone number. A real SIM is
required to message a real number.

## A note on filesystems

If you keep a clone on an exFAT or FAT32 volume — common for external drives —
macOS writes AppleDouble `._*` sidecar files next to every file. These break the
toolchain in confusing ways: `flutter test` fails decoding `._*.dart` as UTF-8,
and AAPT treats `._drawable-v21` as a resource directory. Clean them first:

```bash
find . -name '._*' -not -path './.git/*' -delete
```

Working from a case-sensitive APFS or ext4 volume avoids the problem entirely.
