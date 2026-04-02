package com.example.native_android_sms_retrieval_with_flutter

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterFragmentActivity() {

    private val CHANNEL = "com.example.native_android_sms_retrieval_with_flutter"
    private val TAG = "SMS_AUTH_DEBUG"
    private val SMS_CONSENT_REQUEST = 101
    private var methodChannel: MethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)

        methodChannel?.setMethodCallHandler { call, result ->
            if (call.method == "startSmsListener") {
                Log.d(TAG, "Flutter invoked startSmsListener")
                startSmsUserConsent()
                result.success(null)
            } else {
                result.notImplemented()
            }
        }
    }

    private fun startSmsUserConsent() {
        Log.d(TAG, "Initializing SMS User Consent API...")
        val client = SmsRetriever.getClient(this)

        client.startSmsUserConsent(null).addOnSuccessListener {
            Log.d(TAG, "SmsRetriever started successfully. Listening for SMS...")
        }.addOnFailureListener {
            Log.e(TAG, "SmsRetriever failed to start: ${it.message}")
        }

        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)

        // Use ContextCompat to safely handle the Exported flag required by Android 13+
        ContextCompat.registerReceiver(
            this,
            smsVerificationReceiver,
            intentFilter,
            SmsRetriever.SEND_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )
        Log.d(TAG, "BroadcastReceiver registered via ContextCompat.")
    }

    private val smsVerificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                val extras = intent.extras
                val status = extras?.get(SmsRetriever.EXTRA_STATUS) as Status

                when (status.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        Log.d(TAG, "SMS detected. Launching consent bottom sheet.")
                        val consentIntent = extras.getParcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)
                        try {
                            startActivityForResult(consentIntent!!, SMS_CONSENT_REQUEST)
                        } catch (e: ActivityNotFoundException) {
                            Log.e(TAG, "Error starting consent activity: ${e.message}")
                        }
                    }
                    CommonStatusCodes.TIMEOUT -> {
                        Log.w(TAG, "SmsRetriever timed out.")
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SMS_CONSENT_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
                message?.let {
                    Log.d(TAG, "Message received: $it")
                    val otp = extractOtp(it)
                    if (otp != null) {
                        Log.i(TAG, "OTP Extracted: $otp. Sending to Flutter.")
                        methodChannel?.invokeMethod("onOtpReceived", otp)
                    } else {
                        Log.e(TAG, "Could not find 8-digit code in message.")
                    }
                }
            } else {
                Log.w(TAG, "User denied SMS consent.")
            }
        }
    }

    private fun extractOtp(message: String): String? {
        // Matches exactly 8 Alphanumeric characters
        val otpPattern = Regex("([A-Z0-9]{8})")
        return otpPattern.find(message)?.groupValues?.get(1)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(smsVerificationReceiver)
            Log.d(TAG, "Receiver unregistered.")
        } catch (e: Exception) { }
        super.onDestroy()
    }
}