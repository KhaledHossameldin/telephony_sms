// Widget tests for the example app.
//
// These exercise only the UI: the plugin's platform channel is not available in
// a widget test, so the buttons are checked for presence rather than tapped.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:telephony_sms_example/main.dart';

void main() {
  testWidgets('renders the permission and send actions', (tester) async {
    await tester.pumpWidget(const MyApp());

    expect(
      find.widgetWithText(ElevatedButton, 'Check Permission'),
      findsOneWidget,
    );
    expect(find.widgetWithText(ElevatedButton, 'Send SMS'), findsOneWidget);
  });

  testWidgets('renders the phone and message fields', (tester) async {
    await tester.pumpWidget(const MyApp());

    expect(find.widgetWithText(TextField, 'Phone number'), findsOneWidget);
    expect(find.byType(TextField), findsNWidgets(2));
  });
}
