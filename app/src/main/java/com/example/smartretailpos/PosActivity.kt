package com.example.smartretailpos

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class PosActivity : AppCompatActivity() {

    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var etBarcode: EditText
    private lateinit var etNominalPos: EditText
    private lateinit var rgSimulation: RadioGroup
    private lateinit var rbNormal: RadioButton
    private lateinit var rbError404: RadioButton
    private lateinit var rbErrorTimeout: RadioButton
    private lateinit var btnProcessPayment: Button
    private lateinit var pbPosLoading: ProgressBar
    private lateinit var cardPosResult: CardView
    private lateinit var tvPosResultStatus: TextView
    private lateinit var tvPosDetails: TextView
    private lateinit var btnKembali: Button

    private var retryAttempt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pos)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.coordinatorLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupListeners()
    }

    private fun initViews() {
        coordinatorLayout = findViewById(R.id.coordinatorLayout)
        etBarcode = findViewById(R.id.etBarcode)
        etNominalPos = findViewById(R.id.etNominalPos)
        rgSimulation = findViewById(R.id.rgSimulation)
        rbNormal = findViewById(R.id.rbNormal)
        rbError404 = findViewById(R.id.rbError404)
        rbErrorTimeout = findViewById(R.id.rbErrorTimeout)
        btnProcessPayment = findViewById(R.id.btnProcessPayment)
        pbPosLoading = findViewById(R.id.pbPosLoading)
        cardPosResult = findViewById(R.id.cardPosResult)
        tvPosResultStatus = findViewById(R.id.tvPosResultStatus)
        tvPosDetails = findViewById(R.id.tvPosDetails)
        btnKembali = findViewById(R.id.btnKembali)
    }

    private fun setupListeners() {
        btnProcessPayment.setOnClickListener {
            val barcode = etBarcode.text.toString().trim()
            if (barcode.isEmpty()) {
                Toast.makeText(this, "Barcode wajib diisi atau dipindai", Toast.LENGTH_SHORT).show()
                Timber.w("Proses transaksi dibatalkan: Barcode kosong")
                return@setOnClickListener
            }
            eksekusiTransaksi(barcode, bypassLimitCheck = false)
        }

        btnKembali.setOnClickListener {
            finish()
        }
    }

    private fun eksekusiTransaksi(barcode: String, bypassLimitCheck: Boolean = false) {
        hideKeyboard()

        // Set Loading State (FR-44)
        pbPosLoading.visibility = View.VISIBLE
        cardPosResult.visibility = View.GONE
        btnProcessPayment.isEnabled = false

        // Level 2: CoroutineExceptionHandler sebagai jaring pengaman terakhir (FR-45)
        val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "Kesalahan sistem tak terduga")
            lifecycleScope.launch(Dispatchers.Main) {
                pbPosLoading.visibility = View.GONE
                btnProcessPayment.isEnabled = true
                showSnackbar("Kesalahan sistem tak terduga: ${throwable.message}", canRetry = false)
            }
        }

        // Level 1: Scope launch + IO Dispatchers (FR-45)
        lifecycleScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val nominalInput = etNominalPos.text.toString().toDoubleOrNull() ?: 21500.0

            val result = runCatching {
                // Check cashier limit (Tugas 2 - FR-71)
                if (!bypassLimitCheck && nominalInput > 10_000_000.0) {
                    throw CashierLimitExceededException(10_000_000.0)
                }

                // Simulasi delay jaringan 1.2 detik (FR-46)
                delay(1200)

                // Simulasi skenario error (FR-42)
                when {
                    rbError404.isChecked -> {
                        throw ProductBarcodeNotFoundException(barcode)
                    }
                    rbErrorTimeout.isChecked -> {
                        throw PaymentGatewayTimeoutException("QRIS / EDC Minimarket")
                    }
                    else -> {
                        PosTransaction(
                            transactionId = "TRX-2026-9901",
                            barcode = barcode,
                            productName = "Susu UHT Full Cream 1 Liter",
                            totalAmount = nominalInput,
                            status = "SETTLED_SUCCESS"
                        )
                    }
                }
            }.onFailure { error ->
                // Catatan Teknis #4: melempar ulang CancellationException
                if (error is CancellationException) {
                    throw error
                }
            }

            // Pembaruan UI di Main Thread (FR-48)
            withContext(Dispatchers.Main) {
                pbPosLoading.visibility = View.GONE
                btnProcessPayment.isEnabled = true

                result.onSuccess { trx ->
                    retryAttempt = 0 // Reset counter saat sukses (FR-63)
                    tampilkanSukses(trx)
                }.onFailure { error ->
                    tangkapFailure(error, barcode, nominalInput)
                }
            }
        }
    }

    private fun tampilkanSukses(trx: PosTransaction) {
        cardPosResult.visibility = View.VISIBLE
        tvPosResultStatus.text = "TRANSAKSI BERHASIL (LUNAS)"

        val formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("id", "ID")).format(Date(trx.timestamp))
        val totalFormatted = String.format(Locale("id", "ID"), "Rp %,.2f", trx.totalAmount)

        // Fix Catatan Teknis #1: Format Total Bayar terisi dengan benar
        tvPosDetails.text = """
            ID Transaksi: ${trx.transactionId}
            Barcode: ${trx.barcode}
            Nama Produk: ${trx.productName}
            Total Bayar: $totalFormatted
            Waktu: $formattedDate
        """.trimIndent()

        Timber.i("Transaksi Berhasil: ID=%s, Product=%s, Total=%s", trx.transactionId, trx.productName, totalFormatted)
    }

    private fun tangkapFailure(error: Throwable, barcode: String, nominalInput: Double) {
        when (error) {
            is CashierLimitExceededException -> {
                Timber.w(error, "Otorisasi kasir diperlukan: Total nominal Rp %,.0f melebihi limit Rp %,.0f", nominalInput, error.maxLimit)
                tampilkanDialogPinSupervisor(barcode, error.maxLimit)
            }

            is ProductBarcodeNotFoundException -> {
                Timber.w(error, "Barcode %s tidak terdaftar di katalog", barcode)
                showSnackbar(error.message ?: "Barcode tidak ditemukan", canRetry = false)
            }

            is PaymentGatewayTimeoutException -> {
                retryAttempt++ // Counter hanya bertambah untuk kegagalan jaringan (FR-60, FR-64)
                Timber.w(error, "Payment gateway timeout. Percobaan ke-%d", retryAttempt)

                if (retryAttempt >= 3) {
                    // Tugas 1: Max Retry Threshold reached -> Fallback dialog (FR-61, FR-62)
                    tampilkanDialogFallbackTunai()
                } else {
                    showSnackbar(error.message ?: "Payment gateway timeout", canRetry = true)
                }
            }

            else -> {
                Timber.w(error, "Kegagalan operasional: %s", error.message)
                showSnackbar("Kegagalan operasional: ${error.message}", canRetry = true)
            }
        }
    }

    private fun showSnackbar(message: String, canRetry: Boolean) {
        val snackbar = Snackbar.make(coordinatorLayout, message, if (canRetry) Snackbar.LENGTH_INDEFINITE else Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(Color.parseColor("#991B1B")) // Merah gelap
        snackbar.setTextColor(Color.WHITE)

        if (canRetry) {
            snackbar.setAction("COBA LAGI") {
                val barcode = etBarcode.text.toString().trim()
                Timber.i("Pengguna menekan tombol COBA LAGI untuk barcode=%s", barcode)
                eksekusiTransaksi(barcode, bypassLimitCheck = false)
            }
            snackbar.setActionTextColor(Color.parseColor("#FEF08A")) // Kuning
        }

        snackbar.show()
    }

    // Tugas 1: Max Retry Threshold & Fallback Mode (FR-61, FR-62, FR-63)
    private fun tampilkanDialogFallbackTunai() {
        AlertDialog.Builder(this)
            .setTitle("Jaringan Payment Timeout (3x Gagal)")
            .setMessage("Layanan pembayaran tidak merespons setelah 3 kali percobaan. Direkomendasikan untuk mengalihkan transaksi ke 'Pembayaran Tunai Manual'.")
            .setCancelable(false)
            .setPositiveButton("Pembayaran Tunai Manual") { dialog, _ ->
                retryAttempt = 0 // Reset counter saat fallback dipilih (FR-63)
                Timber.i("Kasir memilih fallback Pembayaran Tunai Manual")
                Toast.makeText(this, "Transaksi dialihkan ke Pembayaran Tunai Manual", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .setNegativeButton("Tutup") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // Tugas 2: Custom Authorization Exception & PIN Dialog (FR-72, FR-73, FR-74)
    private fun tampilkanDialogPinSupervisor(barcode: String, maxLimit: Double) {
        val inputPin = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Masukkan PIN (Default: 1234)"
        }

        val container = android.widget.FrameLayout(this).apply {
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
            addView(inputPin)
        }

        AlertDialog.Builder(this)
            .setTitle("Otorisasi Supervisor Required")
            .setMessage("Total transaksi melebihi limit Rp ${String.format(Locale("id", "ID"), "%,.0f", maxLimit)}. Masukkan PIN Supervisor untuk melanjutkan:")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Otorisasi") { dialog, _ ->
                val pin = inputPin.text.toString().trim()
                if (pin == "1234") {
                    Timber.i("Otorisasi PIN Supervisor berhasil")
                    Toast.makeText(this, "Otorisasi Disetujui oleh Supervisor", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    // Lanjutkan transaksi dengan bypass limit check
                    eksekusiTransaksi(barcode, bypassLimitCheck = true)
                } else {
                    Timber.w("Otorisasi gagal: PIN Supervisor salah")
                    Toast.makeText(this, "PIN Supervisor Salah! Transaksi Dibatalkan", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Batal") { dialog, _ ->
                Timber.i("Otorisasi Supervisor dibatalkan oleh pengguna")
                dialog.dismiss()
            }
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let {
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
}
