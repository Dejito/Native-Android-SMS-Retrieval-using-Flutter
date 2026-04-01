import 'dart:developer' as dev;
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class OTPScreen extends StatefulWidget {
  const OTPScreen({super.key});

  @override
  State<OTPScreen> createState() =>
      _OTPScreenState();
}

class _OTPScreenState extends State<OTPScreen> {
  static const platform = MethodChannel('com.example.native_android_sms_retrieval_with_flutter');

  final TextEditingController _tokenController = TextEditingController();

  bool isButtonEnabled = false;
  final int tokenLength = 8;

  @override
  void initState() {
    super.initState();

    if (Platform.isAndroid) {
      platform.setMethodCallHandler(_handleNativeCall);
      _startSmsListener();
    }
  }

  Future<void> _startSmsListener() async {
    try {
      await platform.invokeMethod('startSmsListener');
      dev.log("Started SMS listener", name: "SMS_AUTH");
    } on PlatformException catch (e) {
      dev.log("Error: ${e.message}", name: "SMS_AUTH");
    }
  }

  Future<void> _handleNativeCall(MethodCall call) async {
    if (call.method == "onOtpReceived") {
      final String otp = call.arguments.toString();

      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;

        setState(() {
          _tokenController.text = otp;
          _updateButtonState();
        });
      });
    }
  }

  void _updateButtonState() {
    isButtonEnabled = _tokenController.text.trim().length == tokenLength;
  }

  @override
  void dispose() {
    _tokenController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("OTP Verification")),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            const SizedBox(height: 40),

            const Text(
              "Enter the 8-digit OTP",
              style: TextStyle(fontSize: 18),
            ),

            const SizedBox(height: 20),

            TextField(
              controller: _tokenController,
              maxLength: tokenLength,
              keyboardType: TextInputType.text,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                hintText: "Enter OTP",
              ),
              onChanged: (_) {
                setState(_updateButtonState);
              },
            ),

            const SizedBox(height: 20),

            ElevatedButton(
              onPressed: isButtonEnabled
                  ? () {
                dev.log(
                  "OTP Submitted: ${_tokenController.text}",
                  name: "SMS_AUTH",
                );
              }
                  : null,
              child: const Text("Verify"),
            ),
          ],
        ),
      ),
    );
  }
}