import 'package:flutter/material.dart';

import 'package:telephony_sms/telephony_sms.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(home: HomePage());
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final _telephonySMS = TelephonySMS();
  final _phoneController = TextEditingController();
  final _messageController = TextEditingController(text: 'Hello from Flutter!');

  @override
  void dispose() {
    _phoneController.dispose();
    _messageController.dispose();
    super.dispose();
  }

  void _report(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _requestPermission() async {
    try {
      final granted = await _telephonySMS.requestPermission();
      _report(granted ? 'Permission granted' : 'Permission denied');
    } on TelephonySmsException catch (e) {
      _report('Could not request permission: ${e.message}');
    }
  }

  Future<void> _sendSMS() async {
    try {
      await _telephonySMS.sendSMS(
        phone: _phoneController.text,
        message: _messageController.text,
      );
      _report('Message handed to the radio');
    } on TelephonySmsException catch (e) {
      _report('${e.code.name}: ${e.message}');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('TelephonySMS Plugin example app')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _phoneController,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(
                labelText: 'Phone number',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _messageController,
              decoration: const InputDecoration(
                labelText: 'Message',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: _requestPermission,
              child: const Text('Check Permission'),
            ),
            const SizedBox(height: 12),
            ElevatedButton(
              onPressed: _sendSMS,
              child: const Text('Send SMS'),
            ),
          ],
        ),
      ),
    );
  }
}
