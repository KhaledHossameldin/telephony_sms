# Security Policy

## Supported versions

| Version | Supported |
| ------- | --------- |
| 0.1.x   | Yes       |
| < 0.1.0 | No        |

Only the latest minor release receives security fixes. Older releases contain
known correctness bugs and should not be used.

## Reporting a vulnerability

Please **do not** open a public issue for a security problem.

Report it privately through
[GitHub Security Advisories](https://github.com/KhaledHossameldin/telephony_sms/security/advisories/new),
or by email to khaled-hossam@outlook.com.

Include the plugin version, Android API level, and steps to reproduce. You can
expect an initial response within seven days.

## Why this matters here

This plugin sends SMS messages without user confirmation, using the restricted
`SEND_SMS` permission. A defect that causes unintended sends costs users real
money and can leak message content to the wrong recipient. Reports in these areas
are treated as security issues rather than ordinary bugs:

- Messages sent to a recipient other than the one requested.
- Messages sent without the caller invoking `sendSMS`, or sent more than once per
  call.
- The permission state being misreported, so a caller believes a send was
  authorized when it was not.
- Message content being written to logs or otherwise exposed.

## Scope

The permission model is deliberately delegated to the host app: this plugin does
not declare `SEND_SMS` in its own manifest. An app that declares the permission
and calls `sendSMS` is working as designed — that is not a vulnerability in the
plugin.
