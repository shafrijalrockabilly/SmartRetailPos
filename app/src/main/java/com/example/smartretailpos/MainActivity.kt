package com.example.smartretailpos

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import timber.log.Timber
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var etNominal: EditText
    private lateinit var etQty: EditText
    private lateinit var btnHitung: Button
    private lateinit var tvHasil: TextView
    private lateinit var btnKePos: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etNominal = findViewById(R.id.etNominal)
        etQty = findViewById(R.id.etQty)
        btnHitung = findViewById(R.id.btnHitung)
        tvHasil = findViewById(R.id.tvHasil)
        btnKePos = findViewById(R.id.btnKePos)

        btnHitung.setOnClickListener {
            hitungTotal()
        }

        btnKePos.setOnClickListener {
            val intent = Intent(this, PosActivity::class.java)
            startActivity(intent)
        }
    }

    private fun hitungTotal() {
        val nominalStr = etNominal.text.toString()
        val qtyStr = etQty.text.toString()

        Timber.d("Proses kalkulasi: nominalStr=%s, qtyStr=%s", nominalStr, qtyStr)

        val result = runCatching {
            val nominal = nominalStr.toDoubleOrNull()
                ?: throw NumberFormatException("Nominal tidak boleh kosong atau non-numerik")
            val qty = qtyStr.toIntOrNull()
                ?: throw NumberFormatException("Kuantitas tidak boleh kosong atau non-numerik")

            if (qty <= 0) {
                throw IllegalArgumentException("Kuantitas barang minimal 1")
            }

            nominal * qty
        }

        result.onSuccess { total ->
            val totalFormatted = String.format(Locale("id", "ID"), "Total Transaksi: Rp %,.2f", total)
            tvHasil.text = totalFormatted
            Timber.i("Kalkulasi berhasil: Nominal=%s, Qty=%s, Total=%s", nominalStr, qtyStr, total)
        }.onFailure { error ->
            Timber.e(error, "Kalkulasi gagal untuk input nominal=%s, qty=%s", nominalStr, qtyStr)
            val errorMessage = error.message ?: "Input tidak valid"
            tvHasil.text = "Error Input: $errorMessage"
        }
    }
}
