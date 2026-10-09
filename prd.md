# Product Requirements Document (PRD)

## SmartRetailPOS — Terminal Kasir Mandiri Berketahanan Tinggi

| Atribut | Keterangan |
|---|---|
| **Nama Produk** | SmartRetailPOS |
| **Package** | `com.industri.smartpos` |
| **Platform** | Android (Kotlin), minSdk API 24+ |
| **Mata Kuliah** | Pemrograman Mobile Dasar — Pertemuan 12 |
| **Topik** | Error Handling & Teknik Debugging Aplikasi Mobile |
| **Institusi** | STMIK IKMI Cirebon — Prodi Sistem Informasi |
| **Dosen Pengampu** | Rosidin, S.Kom., M.Kom. |
| **Metode** | Hands-on Lab (Case-Based Learning) |
| **Versi Dokumen** | 1.0 |
| **Status** | Draft |

---

## 1. Ringkasan Eksekutif

SmartRetailPOS adalah aplikasi simulasi terminal kasir mandiri (self-checkout) untuk minimarket. Fokus utama produk **bukan** pada kelengkapan fitur ritel, melainkan pada **ketahanan sistem (fault tolerance)**: aplikasi tidak boleh *force close* ketika jaringan payment gateway putus, barcode tidak ditemukan, atau input pengguna tidak valid. Setiap kegagalan harus:

1. Ditangkap dengan strategi penanganan eksepsi bertingkat,
2. Dicatat (log) menggunakan Timber hanya pada build debug,
3. Dikomunikasikan ke pengguna dengan pesan yang ramah dan solutif,
4. Dipulihkan melalui mekanisme *Retry* bila memungkinkan.

Aplikasi sekaligus berfungsi sebagai media belajar penggunaan **Android Studio Interactive Debugger**.

## 2. Latar Belakang & Masalah

Di industri ritel modern, kasir mandiri melayani ratusan transaksi per jam. Masalah yang harus diantisipasi:

- Jaringan minimarket tiba-tiba putus saat pelanggan menekan "Bayar".
- Barcode yang dipindai rusak atau tidak terdaftar di katalog.
- Input kosong/tidak valid yang memicu crash (`NumberFormatException`).
- Log debug yang tertinggal di APK rilis dan membocorkan token, URL server, atau data sensitif lewat Logcat.
- Eksepsi yang "ditelan" diam-diam (*swallowed exception*) sehingga bug mustahil dilacak di produksi.

## 3. Tujuan Produk

### 3.1 Tujuan Pembelajaran (Sub-CPMK 12)

Mahasiswa mampu:

1. Mengidentifikasi kategori galat: Crash Fatal, ANR, Eksepsi Jaringan, dan Bug Logika.
2. Menerapkan `try-catch`, `runCatching`, dan `CoroutineExceptionHandler`.
3. Mengintegrasikan **Timber** untuk logging yang steril dari rilis produksi.
4. Mengoperasikan debugger Android Studio (Breakpoint, Step Over, Step Into, Watch, Evaluate Expression).
5. Membangun arsitektur status galat UI (*UI Error State*) beserta *Retry Pattern*.

### 3.2 Tujuan Produk

- Aplikasi tidak crash pada seluruh skenario error yang disimulasikan.
- Pengguna selalu mendapat umpan balik yang jelas (sukses atau gagal).
- Log hanya aktif pada build debug.

### 3.3 Non-Tujuan (Out of Scope)

- Integrasi payment gateway sungguhan.
- Database/katalog produk nyata (data produk disimulasikan).
- Autentikasi pengguna/kasir (selain dialog PIN Supervisor pada tugas mandiri).
- Cetak struk, manajemen stok, laporan penjualan.

## 4. Target Pengguna

| Persona | Deskripsi | Kebutuhan |
|---|---|---|
| **Kasir / Pelanggan self-checkout** | Pengguna yang memindai barcode dan membayar | Pesan error yang mudah dipahami, tombol coba lagi, tidak macet |
| **Supervisor Kasir** | Pihak yang mengotorisasi transaksi bernilai besar | Dialog PIN untuk menyetujui transaksi > Rp 10.000.000 |
| **Mahasiswa / Developer** | Pelaksana praktikum | Kode yang dapat di-debug, log informatif, skenario error yang dapat direproduksi |

## 5. Ruang Lingkup Fitur

Aplikasi terdiri dari dua modul layar:

| Modul | Activity | Tujuan |
|---|---|---|
| **Tahap 1 — Latihan Debugging** | `MainActivity` | Kalkulator total transaksi (nominal × qty) untuk latihan breakpoint |
| **Tahap 2 — Studi Kasus POS** | `PosActivity` | Simulasi pembayaran dengan penanganan eksepsi bertingkat dan retry |

## 6. Kebutuhan Fungsional

### 6.1 Konfigurasi Logging (Timber)

| ID | Kebutuhan | Prioritas |
|---|---|---|
| FR-01 | Aplikasi memiliki kelas `MyApplication : Application` yang menginisialisasi Timber | Must |
| FR-02 | `Timber.DebugTree()` hanya ditanam (`plant`) bila `BuildConfig.DEBUG == true` | Must |
| FR-03 | `MyApplication` didaftarkan pada `AndroidManifest.xml` via `android:name=".MyApplication"` | Must |
| FR-04 | Log menggunakan format varargs Timber (mis. `Timber.d("Nominal=%s", x)`) bukan konkatenasi string | Should |
| FR-05 | Level log dipakai sesuai fungsi: `d` (proses), `i` (sukses), `w` (penolakan/gagal bisnis), `e` (error + stacktrace) | Should |

### 6.2 Modul Kalkulasi — `MainActivity` (Tahap 1)

| ID | Kebutuhan | Prioritas |
|---|---|---|
| FR-10 | Layar `activity_main` memiliki `etNominal`, `etQty`, `btnHitung`, `tvHasil` | Must |
| FR-11 | Saat `btnHitung` ditekan, nilai input dibaca dan dicatat via `Timber.d` | Must |
| FR-12 | Kalkulasi `nominal * qty` dibungkus `runCatching` | Must |
| FR-13 | Bila `qty <= 0`, lempar `IllegalArgumentException("Kuantitas barang minimal 1")` | Must |
| FR-14 | `onSuccess`: tampilkan "Total Transaksi: Rp %,.2f" dan catat `Timber.i` | Must |
| FR-15 | `onFailure`: catat `Timber.e(error, ...)` dan tampilkan "Error Input: <pesan>" (fallback "Input tidak valid") | Must |
| FR-16 | Input kosong / non-numerik tidak boleh membuat aplikasi crash | Must |
| FR-17 | Kode siap diuji dengan breakpoint pada baris `val nominalStr = etNominal.text.toString()` | Must |

### 6.3 Arsitektur Status UI

| ID | Kebutuhan | Prioritas |
|---|---|---|
| FR-20 | `UiState<out T>` sealed class dengan state: `Idle`, `Loading`, `Success<T>(data)`, `Error(message, errorCode?, canRetry)` | Must |
| FR-21 | `Error.canRetry` default `true`; `errorCode` default `null` | Must |
| FR-22 | Data class `PosTransaction(transactionId, barcode, productName, totalAmount, status)` | Must |

### 6.4 Custom Exception (`RetailExceptions.kt`)

| ID | Kelas | Turunan | Pemicu | Pesan |
|---|---|---|---|---|
| FR-30 | `ProductBarcodeNotFoundException(barcode)` | `Exception` | Barcode tidak ada di katalog | "Barang dengan barcode [$barcode] tidak terdaftar pada katalog sistem." |
| FR-31 | `PaymentGatewayTimeoutException(gatewayName)` | `IOException` | Gateway pembayaran timeout | "Layanan pembayaran $gatewayName tidak merespons dalam 15 detik." |
| FR-32 | `CashierLimitExceededException(maxLimit)` | `Exception` | Total melebihi limit kasir | "Total transaksi melebihi limit otorisasi kasir mandiri (Maksimal Rp %,.0f)" |

### 6.5 Layar POS — `PosActivity` (Tahap 2)

| ID | Kebutuhan | Prioritas |
|---|---|---|
| FR-40 | Layar `activity_pos` berisi: judul, subjudul, `etBarcode`, `rgSimulation` (3 RadioButton), `btnProcessPayment`, `pbPosLoading`, `cardPosResult` | Must |
| FR-41 | Layout root menggunakan `CoordinatorLayout` (`id=coordinatorLayout`) agar Snackbar dapat ditampilkan | Must |
| FR-42 | Pilihan simulasi: **Normal (berhasil)** (default), **Error 404 barcode tidak ditemukan**, **Error jaringan (payment timeout)** | Must |
| FR-43 | Validasi: bila barcode kosong, tampilkan Toast "Barcode wajib diisi atau dipindai" dan tidak memulai transaksi | Must |
| FR-44 | Saat transaksi berjalan: `ProgressBar` tampil, kartu hasil disembunyikan, tombol dinonaktifkan | Must |
| FR-45 | Transaksi dijalankan di `lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler)` | Must |
| FR-46 | Latensi jaringan disimulasikan dengan `delay(1200)` | Must |
| FR-47 | Logika transaksi dibungkus `runCatching` dan menghasilkan `Result<PosTransaction>` | Must |
| FR-48 | Pembaruan UI dilakukan pada `Dispatchers.Main` via `withContext` | Must |
| FR-49 | Setelah selesai (sukses/gagal): `ProgressBar` hilang dan tombol aktif kembali | Must |

### 6.6 Penanganan Hasil Transaksi

| Skenario | Eksepsi | UI | Retry | Level Log |
|---|---|---|---|---|
| Normal | — | Kartu hasil: "TRANSAKSI BERHASIL (LUNAS)" + ID, Produk, Total Bayar, Waktu | — | `Timber.i` |
| Barcode tidak ditemukan | `ProductBarcodeNotFoundException` | Snackbar merah gelap (`#991B1B`), pesan eksepsi | **Tidak** | `Timber.w` |
| Payment timeout | `PaymentGatewayTimeoutException` | Snackbar merah gelap + aksi **"COBA LAGI"** (kuning `#FEF08A`) | **Ya** | `Timber.w` |
| Eksepsi lain | `else` | Snackbar "Kegagalan operasional: <pesan>" + aksi "COBA LAGI" | **Ya** | `Timber.w` |
| Error tak tertangkap di coroutine | Any | Snackbar "Kesalahan sistem tak terduga: <pesan>" tanpa retry; loading dihentikan | Tidak | `Timber.e` |

| ID | Kebutuhan | Prioritas |
|---|---|---|
| FR-50 | Aksi "COBA LAGI" membaca ulang barcode dan memanggil `eksekusiTransaksi()` | Must |
| FR-51 | Data transaksi sukses simulasi: ID `TRX-2026-9901`, produk "Susu UHT Full Cream 1 Liter", total Rp 21.500, status `SETTLED_SUCCESS` | Must |
| FR-52 | Dilarang *empty catch block*; setiap eksepsi wajib dicatat dan diinformasikan ke pengguna | Must |

### 6.7 Tugas Mandiri (Fitur Tambahan)

#### Tugas 1 — Max Retry Threshold & Fallback Mode

| ID | Kebutuhan | Prioritas |
|---|---|---|
| FR-60 | Tambahkan variabel `retryAttempt` untuk menghitung penekanan "COBA LAGI" berturut-turut | Must |
| FR-61 | Bila `retryAttempt` mencapai **3** dan jaringan masih gagal, tampilkan `AlertDialog` | Must |
| FR-62 | Dialog merekomendasikan kasir mengalihkan pelanggan ke **"Pembayaran Tunai Manual"** | Must |
| FR-63 | Counter di-reset ke 0 saat transaksi sukses atau saat fallback dipilih | Should |
| FR-64 | Counter hanya bertambah untuk kegagalan yang dapat di-retry (jaringan) | Should |

#### Tugas 2 — Custom Authorization Exception

| ID | Kebutuhan | Prioritas |
|---|---|---|
| FR-70 | Tambahkan input total/nominal belanja pada layar POS | Must |
| FR-71 | Bila total > **Rp 10.000.000**, lempar `CashierLimitExceededException(maxLimit = 10_000_000.0)` | Must |
| FR-72 | Tangkap eksepsi tersebut pada blok `onFailure` dan tampilkan dialog **PIN Supervisor** | Must |
| FR-73 | PIN valid → transaksi dilanjutkan; PIN salah → tampilkan pesan error dan catat log | Must |
| FR-74 | Eksepsi ini **tidak** memiliki retry otomatis (`canRetry = false`) | Should |

## 7. Kebutuhan Non-Fungsional

| ID | Kategori | Kebutuhan |
|---|---|---|
| NFR-01 | Ketahanan | Tidak ada force close pada seluruh skenario simulasi error |
| NFR-02 | Keamanan | Log debug tidak aktif pada build release; tidak ada token/data sensitif dicetak ke log |
| NFR-03 | Observabilitas | Setiap kegagalan tercatat dengan level log yang sesuai, termasuk stacktrace untuk `Timber.e` |
| NFR-04 | Kinerja | Operasi jaringan/IO tidak berjalan di main thread; UI tetap responsif (hindari ANR) |
| NFR-05 | Lifecycle | Coroutine terikat `lifecycleScope` sehingga dibatalkan otomatis saat Activity dihancurkan |
| NFR-06 | Usability | Pesan error berbahasa Indonesia, jelas, dan memberi langkah solusi |
| NFR-07 | Kompatibilitas | Berjalan di emulator API 24+ dan perangkat fisik dengan USB Debugging |
| NFR-08 | Maintainability | Pemisahan berkas: `UiState.kt`, `RetailExceptions.kt`, `MyApplication.kt`, Activity |

## 8. Arsitektur & Teknologi

### 8.1 Tech Stack

| Komponen | Versi / Pustaka |
|---|---|
| Bahasa | Kotlin |
| IDE | Android Studio (Iguana / Jellyfish / Koala / Ladybug atau lebih baru) |
| JDK | 17 (bawaan Android Studio) |
| Logging | `com.jakewharton.timber:timber:5.0.1` |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0` |
| Lifecycle | `androidx.lifecycle:lifecycle-runtime-ktx:2.8.4` |
| UI | Material Components (`libs.material`), AppCompat, ConstraintLayout, CoordinatorLayout |

### 8.2 Struktur Proyek

```
SmartRetailPOS/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml            # android:name=".MyApplication"
│       ├── java/com/industri/smartpos/
│       │   ├── MyApplication.kt           # Inisialisasi Timber (debug only)
│       │   ├── MainActivity.kt            # Latihan debugger (Tahap 1)
│       │   ├── PosActivity.kt             # Logika POS + retry (Tahap 2)
│       │   ├── UiState.kt                 # Sealed class + PosTransaction
│       │   └── RetailExceptions.kt        # Custom exceptions
│       └── res/layout/
│           ├── activity_main.xml          # etNominal, etQty, btnHitung, tvHasil
│           └── activity_pos.xml           # Layout POS
```

### 8.3 Alur Penanganan Eksepsi Bertingkat

```
Klik "Proses Pembayaran"
        │
        ▼
Validasi input (barcode kosong?) ──► Toast, berhenti
        │
        ▼
lifecycleScope.launch(IO + CoroutineExceptionHandler)
        │
        ▼
 runCatching { delay → simulasi → PosTransaction }   ◄── Level 1: tangkap eksepsi bisnis/jaringan
        │
        ▼
 withContext(Main) { hasil.onSuccess / onFailure }
        │                          │
        │                          ├─ ProductBarcodeNotFoundException → Snackbar (tanpa retry)
        │                          ├─ PaymentGatewayTimeoutException  → Snackbar + COBA LAGI
        │                          └─ else                            → Snackbar + COBA LAGI
        ▼
 CoroutineExceptionHandler                           ◄── Level 2: pengaman terakhir
        └─ Timber.e + reset UI + Snackbar (tanpa retry)
```

### 8.4 State Machine UI

```
Idle ──(tekan bayar)──► Loading ──► Success(PosTransaction)
                           │
                           └──► Error(message, errorCode, canRetry)
                                     │
                                     └─(COBA LAGI, jika canRetry)──► Loading
```

## 9. Panduan Debugging (Kebutuhan Praktikum)

| Langkah | Aksi |
|---|---|
| 1 | Pasang breakpoint pada `val nominalStr = etNominal.text.toString()` |
| 2 | Jalankan dengan **Debug app** (ikon serangga / Shift+F9), bukan Run biasa |
| 3 | Isi input di emulator lalu tekan **Hitung** — eksekusi berhenti di breakpoint |
| 4 | Tekan **F8 (Step Over)** untuk melangkah baris demi baris; gunakan **Step Into** untuk masuk fungsi |
| 5 | Hover pada variabel `nominalStr` untuk melihat nilai |
| 6 | Buka panel **Variables** untuk melihat variabel di memori; gunakan **Watch** dan **Evaluate Expression** |

## 10. Kriteria Penerimaan (Acceptance Criteria)

| ID | Skenario | Hasil yang Diharapkan |
|---|---|---|
| AC-01 | Build debug, aksi apa pun | Log Timber muncul di Logcat |
| AC-02 | Build release | Tidak ada log Timber |
| AC-03 | `MainActivity`: nominal `10000`, qty `3` | Tampil "Total Transaksi: Rp 30.000,00" (format sesuai locale) |
| AC-04 | `MainActivity`: nominal kosong | Tampil "Error Input: ...", tidak crash, `Timber.e` tercatat |
| AC-05 | `MainActivity`: qty `0` atau negatif | Tampil "Error Input: Kuantitas barang minimal 1" |
| AC-06 | `PosActivity`: barcode kosong | Toast muncul, tidak ada loading |
| AC-07 | `PosActivity`: mode Normal | Loading ±1,2 detik → kartu "TRANSAKSI BERHASIL (LUNAS)" dengan data benar |
| AC-08 | `PosActivity`: mode Error 404 | Snackbar merah tanpa tombol "COBA LAGI" |
| AC-09 | `PosActivity`: mode Payment Timeout | Snackbar merah dengan tombol "COBA LAGI"; tombol memicu ulang transaksi |
| AC-10 | Loading selesai (apa pun hasilnya) | ProgressBar hilang, tombol bayar aktif |
| AC-11 | Tugas 1: timeout 3× berturut-turut via "COBA LAGI" | `AlertDialog` rekomendasi "Pembayaran Tunai Manual" muncul |
| AC-12 | Tugas 2: total > Rp 10.000.000 | Dialog PIN Supervisor muncul |
| AC-13 | Tugas 2: PIN benar | Transaksi diproses |
| AC-14 | Tugas 2: PIN salah | Pesan error ditampilkan, transaksi tidak diproses |
| AC-15 | Seluruh skenario | Tidak ada force close |

## 11. Skenario Pengujian

| No | Tipe | Langkah | Hasil |
|---|---|---|---|
| T-01 | Positif | Barcode `8991201`, mode Normal, tekan Proses | Transaksi berhasil |
| T-02 | Negatif | Barcode kosong, tekan Proses | Toast validasi |
| T-03 | Negatif | Mode Error 404 | Snackbar tanpa retry |
| T-04 | Negatif | Mode Timeout → COBA LAGI | Transaksi diulang |
| T-05 | Batas | Timeout, COBA LAGI 3× | AlertDialog fallback tunai |
| T-06 | Batas | Total tepat Rp 10.000.000 | Diproses normal (batas bersifat "di atas") |
| T-07 | Batas | Total Rp 10.000.001 | Dialog PIN Supervisor |
| T-08 | Lifecycle | Rotasi layar / tutup Activity saat loading | Tidak crash; coroutine dibatalkan |
| T-09 | Debugging | Breakpoint pada `MainActivity` | Eksekusi berhenti, variabel terlihat di panel Variables |

## 12. Troubleshooting Umum

| Masalah | Penyebab | Solusi |
|---|---|---|
| Log Timber tidak muncul di Logcat | `MyApplication` belum didaftarkan di manifest | Tambahkan `android:name=".MyApplication"` pada `<application>` |
| Aplikasi tetap crash pada coroutine | Tidak ada `CoroutineExceptionHandler` / `SupervisorJob` | Pasang handler pada parameter `launch` |
| Emulator berhenti "Waiting for Debugger" | Opsi *Wait for debugger* aktif di Developer Options | Matikan opsi tersebut atau klik **Attach Debugger** |
| `NullPointerException` saat inisialisasi view | ID salah atau `findViewById` sebelum `setContentView` | Pastikan urutan benar dan ID sesuai layout |

## 13. Catatan Teknis & Potensi Perbaikan pada Kode Modul

Beberapa hal pada kode contoh di modul yang perlu diperhatikan saat implementasi:

1. **Format "Total Bayar" tidak terisi.** Di `tvPosDetails`, string `"Rp %,.2f"` berada di dalam raw string `trimIndent()` tanpa `.format(...)`. Gunakan `${"%,.2f".format(trx.totalAmount)}` agar nilai tampil benar.
2. **Kebutuhan `activity_main.xml`.** Modul tidak menyertakan layout ini; buat sendiri dengan ID `etNominal`, `etQty`, `btnHitung`, `tvHasil`.
3. **Activity pada Manifest.** `PosActivity` (dan `MainActivity`) harus terdaftar di manifest; tentukan salah satu sebagai `LAUNCHER`, atau tambahkan tombol navigasi antar layar.
4. **`runCatching` & pembatalan coroutine.** `runCatching` juga menangkap `CancellationException`, sehingga dapat mengganggu pembatalan coroutine. Pertimbangkan melempar ulang `CancellationException` di dalam `onFailure`.
5. **`CoroutineExceptionHandler` jarang terpicu.** Karena logika utama sudah dibungkus `runCatching`, handler hanya menjadi jaring pengaman terakhir. Untuk mendemonstrasikannya, buat skenario eksepsi di luar blok `runCatching`.
6. **Pewarnaan `canRetry`.** Struktur `UiState.Error` sudah menyediakan `canRetry`; pertimbangkan memakainya langsung sebagai sumber kebenaran pada Snackbar alih-alih parameter terpisah.
7. **`ProgressBar` dan `Toast` import.** Impor `android.widget.*` mencakup `Toast`, `ProgressBar`, dll.; pastikan `CardView` yang diimpor kompatibel dengan `MaterialCardView` di layout (`MaterialCardView` adalah turunan `CardView`).
8. **Locale format angka.** Format `%,.2f` bergantung locale perangkat; gunakan `Locale("id", "ID")` bila ingin pemisah titik/koma sesuai standar Indonesia.

## 14. Rencana Pengerjaan

| Fase | Aktivitas | Output |
|---|---|---|
| 1 | Setup proyek, dependensi Timber, Coroutines, Lifecycle | Proyek ter-build |
| 2 | `MyApplication` + registrasi manifest | Timber aktif di debug |
| 3 | `MainActivity` + latihan debugger | Screenshot breakpoint & panel Variables |
| 4 | `UiState.kt`, `PosTransaction`, `RetailExceptions.kt` | Model & eksepsi |
| 5 | `activity_pos.xml` | Layout POS |
| 6 | `PosActivity.kt` (coroutine, runCatching, Snackbar, retry) | Simulasi 3 skenario berjalan |
| 7 | Tugas 1: retry threshold + AlertDialog fallback | Fitur fallback tunai |
| 8 | Tugas 2: limit Rp 10 juta + dialog PIN Supervisor | Fitur otorisasi |
| 9 | Pengujian seluruh skenario & rekam demo | Video/screen recording hasil (output) |
| 10 | Penyusunan laporan praktikum | Laporan + tautan output |

## 15. Deliverables

- Source code proyek `SmartRetailPOS` (repository atau arsip ZIP).
- Screenshot/rekaman: debugger (breakpoint, Variables), tiga skenario POS, dialog fallback, dialog PIN Supervisor.
- Tautan output demo (mis. Google Drive).
- Laporan praktikum singkat.

## 16. Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|---|---|---|
| Timber tidak terinisialisasi | Log tidak muncul | Verifikasi registrasi manifest |
| Update UI dari thread background | Crash `CalledFromWrongThreadException` | Gunakan `withContext(Dispatchers.Main)` |
| Log sensitif bocor di rilis | Pelanggaran keamanan | `DebugTree` hanya saat `BuildConfig.DEBUG` |
| Eksepsi ditelan | Bug sulit dilacak | Wajib `Timber.e` pada setiap penanganan |
| Retry tak terbatas | Beban jaringan & UX buruk | Batas 3 kali + fallback tunai |

## 17. Glosarium

| Istilah | Arti |
|---|---|
| **Fault Tolerance** | Kemampuan sistem tetap berjalan meski terjadi kegagalan |
| **ANR** | *Application Not Responding* — UI thread terblokir terlalu lama |
| **runCatching** | Idiom Kotlin yang membungkus blok kode menjadi `Result<T>` |
| **CoroutineExceptionHandler** | Penangkap eksepsi yang tidak tertangani di coroutine |
| **Timber** | Pustaka logging oleh Jake Wharton dengan sistem *Tree* |
| **Swallowed Exception** | Eksepsi yang ditangkap tetapi diabaikan tanpa dicatat |
| **Retry Pattern** | Pola memberi kesempatan mengulang operasi yang gagal |
| **Breakpoint** | Titik henti eksekusi pada debugger |
| **Step Over (F8)** | Melangkah ke baris berikutnya tanpa masuk ke dalam fungsi |

---

*Dokumen ini disusun berdasarkan Modul Praktikum Pemrograman Mobile Dasar Pertemuan 12 — STMIK IKMI Cirebon.*
