package com.mark.ocrscan

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wallet.*

class MainActivity : AppCompatActivity(), View.OnClickListener {
    companion object {
        const val TAG = "MainActivity"
    }

    private val tvNumber by lazy {
        findViewById<TextView>(R.id.tv_credit_card_number)
    }

    private val btnGoogleWallet by lazy {
        findViewById<Button>(R.id.btn_google_wallet)
    }

    private val btnMLKit by lazy {
        findViewById<Button>(R.id.btn_ml_kit)
    }

    private lateinit var paymentsClient: PaymentsClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnGoogleWallet.setOnClickListener(this)
        btnMLKit.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v) {
            btnGoogleWallet -> {
                initGoogleWallet()
            }
            btnMLKit -> {
                val permissions = arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )

                permissionLauncher.launch(permissions)
            }
        }
    }

    private fun initGoogleWallet() {
        val walletOptions = Wallet.WalletOptions.Builder()
            .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
            .build()
        val request = PaymentCardRecognitionIntentRequest.getDefaultInstance()

        paymentsClient = Wallet.getPaymentsClient(this, walletOptions)
        paymentsClient
            .getPaymentCardRecognitionIntent(request)
            .addOnSuccessListener { intentResponse ->
                val senderRequest = IntentSenderRequest.Builder(intentResponse.paymentCardRecognitionPendingIntent).build()
                walletResultLauncher.launch(senderRequest)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Payment card ocr not available.", e)
            }
    }

    private fun setCreditCardText(text: String) {
        val cardNumber = text.replace(" ", "")
        if (cardNumber.length == 16) {
            tvNumber.text = cardNumber.substring(0, 4) + "-" + cardNumber.substring(4, 8) + "-" + cardNumber.substring(8, 12) + "-" + cardNumber.substring(12)
        }
    }

    private val walletResultLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val data: Intent? = result.data
        data?.let { intent ->
            val cardResult = PaymentCardRecognitionResult.getFromIntent(intent)
            cardResult?.let {
//                val creditCardExpirationDate = it.creditCardExpirationDate
//                val expirationDate = "%02d/%d".format(creditCardExpirationDate?.month ?: 0, creditCardExpirationDate?.year ?: 0)
                val cardNumber = it.pan
                setCreditCardText(cardNumber)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultMap ->
        var permissionGranted = true
        resultMap.values.iterator().forEach {
            if (!it) {
                permissionGranted = false
            }
        }

        if (permissionGranted) {
            mlKitResultLauncher.launch(MLKitCameraActivity.getActivityIntent(this))
        } else {
            Toast.makeText(this, "Permission not granted", Toast.LENGTH_SHORT).show()
        }

    }

    private val mlKitResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val cardNumber = data?.extras?.getString("CardNumber") ?: ""
            setCreditCardText(cardNumber)
        }
    }
}