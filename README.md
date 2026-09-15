# HARDI MANTANGAI FIRE NOW

Aplikasi web pemantauan titik api (hotspot) berbasis data nyata Near Real-Time (NRT) dari satelit NASA FIRMS untuk wilayah Hardi Mantangai, Kabupaten Kapuas, Provinsi Kalimantan Tengah dan sekitarnya.

Aplikasi ini di-rewrite secara penuh dari proyek Android asli ke dalam arsitektur modern **React + Vite + TypeScript + Tailwind CSS** dengan tetap mempertahankan 100% mandat **Zero Dummy & Evidence-Based**.

---

## 🌟 Fitur Utama

1. **Mandat Zero Dummy & Bukti Otentik (Evidence-Based)**
   - Tidak ada data anomali atau titik api palsu/dummy.
   - Verifikasi ketat checksum SHA-256 pada setiap respons data satelit.
   - Status ditampilkan secara eksplisit: `--` jika belum terverifikasi, `0` jika verifikasi tuntas tanpa anomali, atau angka pasti titik api riil.

2. **Sensor Satelit NASA FIRMS Prioritas**
   - Loop sensor otomatis: `VIIRS NOAA-21 NRT` &rarr; `VIIRS NOAA-20 NRT` &rarr; `VIIRS Suomi-NPP NRT` &rarr; `MODIS (Terra/Aqua) NRT`.
   - Cooldown manual 30 detik & penanganan pembatasan laju (*rate-limit backoff* 60 detik).
   - Pengaturan MAP_KEY lokal browser yang aman tanpa kebocoran server.

3. **Peta Interaktif (Leaflet)**
   - Lapisan Peta Jalan (OpenStreetMap) dan Citra Satelit (Esri World Imagery).
   - Marker titik api dengan kode warna berdasarkan intensitas FRP (*Fire Radiative Power*).
   - Indikator lokasi sensor GPS perangkat dengan lingkaran radius akurasi.
   - Pemusatan cepat ke titik tengah Mantangai atau lokasi pengguna.

4. **Daftar & Penyaring Titik Panas**
   - Pengurutan: *Terbaru*, *Terdekat* (memerlukan GPS aktif), *FRP Tertinggi*.
   - Penyaringan berdasarkan radius jarak (≤5km, ≤10km, ≤25km, ≤50km, ≤100km), sensor satelit, dan usia data.

5. **Navigasi & Berbagi Cepat**
   - Integrasi langsung rute navigasi Google Maps menuju titik koordinat api.
   - Fitur bagikan format laporan terstandarisasi ke WhatsApp.

6. **Identitas Pengembang & Kontak Langsung**
   - Pengembang: **Hardi Mantangai (Suhardi)**
   - Kontak WhatsApp: **0852-1999-1118**

---

## 🚀 Menjalankan Aplikasi

```bash
# Instal dependensi
npm install

# Jalankan server pengembangan (Port 3000)
npm run dev

# Kompilasi build produksi
npm run build
```

## ⚙️ Variabel Lingkungan

Dapat dikonfigurasi melalui `.env`:

```env
FIRMS_MAP_KEY= # MAP_KEY resmi dari NASA FIRMS (opsional jika pengguna memasukkan melalui antarmuka web)
```
