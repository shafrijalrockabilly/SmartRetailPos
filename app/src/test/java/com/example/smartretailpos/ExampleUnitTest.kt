package com.example.smartretailpos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testBarcodeNotFoundException_message() {
        val ex = ProductBarcodeNotFoundException("8991201")
        assertEquals("Barang dengan barcode [8991201] tidak terdaftar pada katalog sistem.", ex.message)
    }

    @Test
    fun testPaymentTimeoutException_message() {
        val ex = PaymentGatewayTimeoutException("QRIS")
        assertEquals("Layanan pembayaran QRIS tidak merespons dalam 15 detik.", ex.message)
    }

    @Test
    fun testCashierLimitExceededException_message() {
        val ex = CashierLimitExceededException(10_000_000.0)
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("10.000.000"))
    }

    @Test
    fun testCalculation_validInput() {
        val nominal = 10000.0
        val qty = 3
        val total = nominal * qty
        assertEquals(30000.0, total, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCalculation_zeroQuantity_throwsException() {
        val qty = 0
        if (qty <= 0) {
            throw IllegalArgumentException("Kuantitas barang minimal 1")
        }
    }

    @Test
    fun testPosTransaction_defaultValues() {
        val trx = PosTransaction(
            transactionId = "TRX-2026-9901",
            barcode = "8991201",
            productName = "Susu UHT",
            totalAmount = 21500.0,
            status = "SETTLED_SUCCESS"
        )
        assertEquals("TRX-2026-9901", trx.transactionId)
        assertTrue(trx.timestamp > 0)
    }
}
