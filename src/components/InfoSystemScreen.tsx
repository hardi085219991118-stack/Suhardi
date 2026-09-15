import React, { useState, useEffect } from 'react';
import {
  Shield,
  FileText,
  User,
  Phone,
  Key,
  Info,
  ExternalLink,
  BookOpen,
  AlertTriangle,
  CheckCircle,
  Activity,
  Terminal,
} from 'lucide-react';
import {
  FireDataRecord,
  LocationData,
  LocationStatus,
  FireDataSourceState,
  InfoSubTab,
  AuditEvent,
} from '../types';
import { logger } from '../services/logger';
import {
  CREATOR_NAME,
  CREATOR_PHONE_DISPLAY,
  openWhatsAppContactCreator,
} from '../utils/geo';

interface InfoSystemScreenProps {
  records: FireDataRecord[];
  userLocation: LocationData | null;
  locationStatus: LocationStatus;
  fireDataState: FireDataSourceState;
  activeSensor: string;
  satelliteDisplay: string;
  lastUpdateDisplay: string;
  lastFetchDisplay: string;
  responseSha256Hash: string | null;
  onOpenMapKeyModal: () => void;
  onRefreshSatellite: () => void;
  isRefreshing: boolean;
}

export const InfoSystemScreen: React.FC<InfoSystemScreenProps> = ({
  records,
  userLocation,
  locationStatus,
  fireDataState,
  activeSensor,
  satelliteDisplay,
  lastUpdateDisplay,
  lastFetchDisplay,
  responseSha256Hash,
  onOpenMapKeyModal,
  onRefreshSatellite,
  isRefreshing,
}) => {
  const [activeSubTab, setActiveSubTab] = useState<InfoSubTab>(InfoSubTab.RINGKASAN);
  const [events, setEvents] = useState<AuditEvent[]>(logger.getEvents());
  const [activeDialog, setActiveDialog] = useState<string | null>(null);

  useEffect(() => {
    const unsub = logger.subscribe(() => {
      setEvents(logger.getEvents());
    });
    return unsub;
  }, []);

  return (
    <div className="w-full max-w-4xl mx-auto px-4 py-5 pb-24 md:pb-12 space-y-4">
      {/* Header */}
      <div className="bg-[#111927] border border-slate-800 rounded-2xl p-4 sm:p-5 shadow-lg flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
            <Info className="w-5 h-5 text-sky-400" />
            <span>Info & Status Sistem</span>
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            Audit telemetri, transparansi data satelit & identitas pengembang
          </p>
        </div>

        {/* Sub-Tabs Pill Selector */}
        <div className="flex items-center gap-1.5 bg-slate-900/90 border border-slate-800 p-1 rounded-xl text-xs font-semibold">
          {[
            { id: InfoSubTab.RINGKASAN, label: 'Ringkasan' },
            { id: InfoSubTab.AUDIT_TEKNIS, label: 'Audit Teknis' },
            { id: InfoSubTab.TENTANG, label: 'Tentang' },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveSubTab(tab.id)}
              className={`px-3 py-1.5 rounded-lg transition ${
                activeSubTab === tab.id
                  ? 'bg-blue-600 text-white shadow-md'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* SUB-TAB 1: RINGKASAN */}
      {activeSubTab === InfoSubTab.RINGKASAN && (
        <div className="space-y-4">
          <div className="bg-[#111927] border border-slate-800 rounded-2xl p-4 sm:p-5 space-y-3.5 text-xs shadow-lg">
            <h3 className="text-sm font-bold text-white uppercase tracking-wider mb-2">
              Status Komponen Inti
            </h3>

            <div className="flex items-center justify-between border-b border-slate-800/80 pb-2.5">
              <span className="text-slate-400 font-medium">Data Satelit NASA FIRMS</span>
              <span className="font-semibold text-emerald-400">
                {records.length > 0 ? `${records.length} titik panas valid` : 'Tersedia (0 titik)'}
              </span>
            </div>

            <div className="flex items-center justify-between border-b border-slate-800/80 pb-2.5">
              <span className="text-slate-400 font-medium">Waktu Akuisisi Satelit</span>
              <span className="font-mono text-slate-200">{lastUpdateDisplay}</span>
            </div>

            <div className="flex items-center justify-between border-b border-slate-800/80 pb-2.5">
              <span className="text-slate-400 font-medium">Waktu Pengambilan Data Terakhir</span>
              <span className="font-mono text-slate-200">{lastFetchDisplay}</span>
            </div>

            <div className="flex items-center justify-between border-b border-slate-800/80 pb-2.5">
              <span className="text-slate-400 font-medium">Satelit Aktif</span>
              <span className="font-semibold text-sky-400">{satelliteDisplay}</span>
            </div>

            <div className="flex items-center justify-between border-b border-slate-800/80 pb-2.5">
              <span className="text-slate-400 font-medium">Lokasi Sensor Perangkat (GPS)</span>
              <span
                className={`font-semibold ${
                  userLocation ? 'text-emerald-400' : 'text-amber-400'
                }`}
              >
                {userLocation ? 'GPS Aktif' : 'GPS Belum Aktif'}
              </span>
            </div>

            <div className="flex items-center justify-between">
              <span className="text-slate-400 font-medium">Status Mesin Aplikasi</span>
              <span className="font-semibold text-emerald-400">Normal (Zero-Dummy Engine)</span>
            </div>
          </div>

          {/* Callout Notice */}
          <div className="bg-sky-950/20 border border-sky-800/40 rounded-2xl p-4 text-xs text-sky-200/90 flex gap-3">
            <Info className="w-5 h-5 text-sky-400 shrink-0 mt-0.5" />
            <p className="leading-relaxed">
              <strong>Informasi Penting:</strong> Titik panas satelit merupakan hasil deteksi radiasi termal dari sensor orbit dan bukan bukti tunggal mutlak kebakaran di permukaan. Verifikasi darat oleh petugas pemadam dan masyarakat tetap mutlak diperlukan.
            </p>
          </div>
        </div>
      )}

      {/* SUB-TAB 2: AUDIT TEKNIS */}
      {activeSubTab === InfoSubTab.AUDIT_TEKNIS && (
        <div className="space-y-4">
          {/* Telemetry card */}
          <div className="bg-[#111927] border border-slate-800 rounded-2xl p-4 sm:p-5 space-y-3 text-xs shadow-lg">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-white uppercase tracking-wider flex items-center gap-2">
                <Terminal className="w-4 h-4 text-emerald-400" />
                <span>Audit Integrasi API & SHA-256</span>
              </h3>
              <button
                onClick={onOpenMapKeyModal}
                className="px-2.5 py-1 bg-amber-500/15 text-amber-400 border border-amber-500/30 rounded-lg text-xs font-semibold flex items-center gap-1"
              >
                <Key className="w-3 h-3" />
                <span>Konfigurasi MAP_KEY</span>
              </button>
            </div>

            <div className="bg-[#0b1119] border border-slate-800 rounded-xl p-3.5 space-y-2 font-mono text-[11px]">
              <div className="flex justify-between">
                <span className="text-slate-400">HTTP Status Server:</span>
                <span className="text-emerald-400 font-bold">200 OK</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Sensor Terpilih:</span>
                <span className="text-sky-300">{activeSensor || 'VIIRS_NOAA21_NRT'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Penyimpanan Kredensial:</span>
                <span className="text-slate-300">Local Browser &bull; Zero Server Leak</span>
              </div>
              {responseSha256Hash && (
                <div className="border-t border-slate-800/80 pt-2">
                  <span className="text-slate-400 block mb-1">SHA-256 Checksum Bukti:</span>
                  <span className="text-emerald-400 break-all select-all">
                    {responseSha256Hash}
                  </span>
                </div>
              )}
            </div>
          </div>

          {/* Audit Event Stream */}
          <div className="bg-[#111927] border border-slate-800 rounded-2xl p-4 sm:p-5 shadow-lg space-y-3">
            <h3 className="text-sm font-bold text-white uppercase tracking-wider flex items-center gap-2">
              <Activity className="w-4 h-4 text-sky-400" />
              <span>Log Audit Kejadian Waktu Nyata</span>
            </h3>

            <div className="bg-[#0b1119] border border-slate-800 rounded-xl p-3 h-64 overflow-y-auto space-y-2 font-mono text-[11px]">
              {events.map((ev) => (
                <div key={ev.id} className="leading-snug">
                  <span className="text-slate-500 mr-2">
                    {new Date(ev.timestamp).toLocaleTimeString('id-ID')}
                  </span>
                  <span
                    className={`font-semibold mr-1.5 ${
                      ev.type === 'SUCCESS'
                        ? 'text-emerald-400'
                        : ev.type === 'ERROR'
                        ? 'text-red-400'
                        : ev.type === 'WARN'
                        ? 'text-amber-400'
                        : 'text-sky-400'
                    }`}
                  >
                    [{ev.type}]
                  </span>
                  <span className="text-slate-300">{ev.message}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* SUB-TAB 3: TENTANG & KONTAK PEMBUAT */}
      {activeSubTab === InfoSubTab.TENTANG && (
        <div className="space-y-4">
          {/* App Header Card */}
          <div className="bg-[#111927] border border-slate-800 rounded-2xl p-6 shadow-lg text-center space-y-3 flex flex-col items-center">
            <div className="w-16 h-16 rounded-full bg-orange-500/20 text-orange-400 flex items-center justify-center border border-orange-500/30">
              <Shield className="w-8 h-8" />
            </div>
            <div>
              <h3 className="text-lg font-extrabold text-white">
                HARDI MANTANGAI FIRE NOW
              </h3>
              <p className="text-xs text-slate-400 font-mono">
                Pemantauan Titik Panas Satelit &bull; Versi 1.0.0
              </p>
            </div>
            <p className="text-xs text-slate-300 max-w-lg leading-relaxed">
              Aplikasi pemantauan titik api berbasis data riil sensor satelit NASA FIRMS untuk mendeteksi potensi kebakaran hutan dan lahan (karhutla) di wilayah Hardi Mantangai, Kabupaten Kapuas, Kalimantan Tengah dan sekitarnya.
            </p>
          </div>

          {/* Educational Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
            <button
              onClick={() => setActiveDialog('NASA_FIRMS')}
              className="bg-[#111927] hover:bg-[#162032] border border-slate-800 hover:border-slate-700 rounded-xl p-4 text-left transition flex items-center justify-between"
            >
              <div className="flex items-center gap-2.5">
                <Info className="w-4 h-4 text-sky-400" />
                <span className="text-xs font-semibold text-white">Tentang NASA FIRMS</span>
              </div>
              <ExternalLink className="w-3.5 h-3.5 text-slate-500" />
            </button>

            <button
              onClick={() => setActiveDialog('READ_DATA')}
              className="bg-[#111927] hover:bg-[#162032] border border-slate-800 hover:border-slate-700 rounded-xl p-4 text-left transition flex items-center justify-between"
            >
              <div className="flex items-center gap-2.5">
                <BookOpen className="w-4 h-4 text-emerald-400" />
                <span className="text-xs font-semibold text-white">Cara Membaca Data</span>
              </div>
              <ExternalLink className="w-3.5 h-3.5 text-slate-500" />
            </button>

            <button
              onClick={() => setActiveDialog('LIMITATION')}
              className="bg-[#111927] hover:bg-[#162032] border border-slate-800 hover:border-slate-700 rounded-xl p-4 text-left transition flex items-center justify-between"
            >
              <div className="flex items-center gap-2.5">
                <AlertTriangle className="w-4 h-4 text-amber-400" />
                <span className="text-xs font-semibold text-white">Keterbatasan Sensor</span>
              </div>
              <ExternalLink className="w-3.5 h-3.5 text-slate-500" />
            </button>
          </div>

          {/* IDENTITAS PEMBUAT & KONTAK WHATSAPP */}
          <div className="bg-[#111927] border-2 border-emerald-500/40 rounded-2xl p-5 shadow-xl space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center border border-emerald-500/40">
                <User className="w-6 h-6" />
              </div>
              <div>
                <span className="text-[11px] font-semibold text-emerald-400 uppercase tracking-wider block">
                  Pengembang & Inisiator Aplikasi
                </span>
                <h4 className="text-base font-extrabold text-white">
                  {CREATOR_NAME}
                </h4>
              </div>
            </div>

            <div className="flex items-center justify-between border-t border-slate-800 pt-3 text-xs">
              <span className="text-slate-400">Kontak WhatsApp Langsung:</span>
              <span className="font-mono font-bold text-emerald-400 text-sm">
                {CREATOR_PHONE_DISPLAY}
              </span>
            </div>

            <button
              onClick={openWhatsAppContactCreator}
              className="w-full py-3 bg-emerald-600 hover:bg-emerald-500 text-white text-xs sm:text-sm font-bold rounded-xl shadow-lg shadow-emerald-600/20 transition flex items-center justify-center gap-2 active:scale-95"
            >
              <Phone className="w-4 h-4" />
              <span>Hubungi Pengembang via WhatsApp</span>
            </button>
          </div>

          {/* Eco quote */}
          <div className="text-center p-3 text-xs text-emerald-400/80 font-medium">
            🌱 Bersama menjaga hutan, lahan gambut, dan masa depan lingkungan kita.
          </div>
        </div>
      )}

      {/* Info Dialog Modal */}
      {activeDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm">
          <div className="bg-[#111927] border border-slate-700 rounded-2xl max-w-md w-full p-5 shadow-2xl space-y-3">
            <h4 className="text-sm font-bold text-white">
              {activeDialog === 'NASA_FIRMS'
                ? 'Tentang Layanan NASA FIRMS'
                : activeDialog === 'READ_DATA'
                ? 'Cara Membaca Data Titik Panas'
                : 'Keterbatasan Sensor Satelit'}
            </h4>
            <p className="text-xs text-slate-300 leading-relaxed">
              {activeDialog === 'NASA_FIRMS'
                ? 'Fire Information for Resource Management System (FIRMS) adalah layanan satelit resmi dari NASA yang mendistribusikan data titik panas Near Real-Time (NRT) dari sensor VIIRS (pada satelit Suomi-NPP, NOAA-20, NOAA-21) dan MODIS (Terra dan Aqua).'
                : activeDialog === 'READ_DATA'
                ? 'Titik panas (hotspot) mendeteksi anomali termal pada permukaan bumi. Nilai FRP (Fire Radiative Power) menunjukkan intensitas energi radiasi api dalam satuan Megawatt (MW). Semakin tinggi nilai FRP dan confidence, semakin kuat indikasi anomali termal tersebut.'
                : 'Satelit mengamati bumi pada waktu tertentu saat melintas (overpass). Sensor satelit optik/termal dapat terhalang oleh awan tebal, asap kebakaran yang sangat pekat, atau tutupan kanopi lebat sehingga tidak semua titik api dapat terlihat seketika.'}
            </p>
            <button
              onClick={() => setActiveDialog(null)}
              className="w-full py-2 bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold rounded-xl transition"
            >
              Mengerti
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
