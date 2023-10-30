import 'package:flutter/material.dart';

import 'package:telephony_sms/telephony_sms.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _telephonySMS = TelephonySMS();

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('TelephonySMS Plugin example app'),
        ),
        body: SizedBox(
          width: double.infinity,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              ElevatedButton(
                onPressed: () async {
                  await _telephonySMS.requestPermission();
                },
                child: const Text('Check Permission'),
              ),
              ElevatedButton(
                onPressed: () {
                  _telephonySMS.sendSMS(phone: "PHONE", message: "MESSAGE");
                },
                child: const Text('Send SMS'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
