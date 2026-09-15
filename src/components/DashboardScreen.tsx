import React from 'react';
import {
  Flame,
  ShieldCheck,
  ShieldAlert,
  MapPin,
  RefreshCw,
  Map,
  List,
  Key,
  Satellite,
  Clock,
  Compass,
  CheckCircle2,
  AlertCircle,
  ExternalLink,
} from 'lucide-react';
import {
  FireDataRecord,
  LocationData,
  LocationStatus,
  FireDataSourceState,
  LiveVerificationGate,
} from '../types';
import { MANTHANGAI_CENTER, DEFAULT_MANTHANGAI_BBOX, formatDistance } from '../utils/geo';

interface DashboardScreenProps {
  records: FireDataRecord[];
  userLocation: LocationData | null;
  locationStatus: LocationStatus;
  fireDataState: FireDataSourceState;
  liveGate: LiveVerificationGate;
  activeSensor: string;
  satelliteDisplay: string;
  lastUpdateDisplay: string;
  dataAgeDisplay: string;
  responseSha256Hash: string | null;
  cooldownSeconds: number;
  isRefreshing: boolean;
  onRequestLocation: () => void;
  onRefreshSatellite: () => void;
  onNavigateToMap: () => void;
  onNavigateToList: () => void;
  onOpenMapKeyModal: () => void;
}

export const DashboardScreen: React.FC<DashboardScreenProps> = ({
  records,
  userLocation,
  locationStatus,
  fireDataState,
  liveGate,
  activeSensor,
  satelliteDisplay,
  lastUpdateDisplay,
  dataAgeDisplay,
  responseSha256Hash,
  cooldownSeconds,
  isRefreshing,
  onRequestLocation,
  onRefreshSatellite,
  onNavigateToMap,
  onNavigateToList,
  onOpenMapKeyModal,
}) => {
  const isSystemVerified =
    (fireDataState === FireDataSourceState.DATA_SOURCE_AVAILABLE ||
      fireDataState === FireDataSourceState.NO_DETECTIONS_IN_QUERY ||
      (fireDataState === FireDataSourceState.CACHED && records.length > 0)) &&
    liveGate === LiveVerificationGate.LIVE_API_VERIFIED;

  // Zero-dummy display logic:
  // When unverified or error: "--"
  // When verified and 0: "0"
  // When verified and >0: records.length
  const fireCountDisplay = isSystemVerified
    ? records.length.toString()
    : '--';

  const statusText = isSystemVerified
    ? `TERVERIFIKASI (${records.length} titik valid)`
    : fireDataState === FireDataSourceState.API_CREDENTIAL_REQUIRED
    ? 'Perlu MAP_KEY Satelit'
    : fireDataState === FireDataSourceState.RATE_LIMIT_EXCEEDED
    ? 'Rate Limit Aktif (Cooldown)'
    : fireDataState === FireDataSourceState.CONNECTING
    ? 'Menghubungi Satelit...'
    : 'Belum Terverifikasi';

  return (
    <div className="w-full max-w-4xl mx-auto px-4 py-5 pb-24 md:pb-12 space-y-4">
      {/* 1. System Verification Header Badge */}
      <div
        className={`border rounded-2xl p-4 sm:p-5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 shadow-lg ${
          isSystemVerified
            ? 'bg-emerald-950/20 border-emerald-500/30'
            : 'bg-amber-950/20 border-amber-500/30'
        }`}
      >
        <div className="flex items-center gap-3">
          <div
            className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 ${
              isSystemVerified
                ? 'bg-emerald-500/20 text-emerald-400'
                : 'bg-amber-500/20 text-amber-400'
            }`}
          >
            {isSystemVerified ? (
              <ShieldCheck className="w-6 h-6" />
            ) : (
              <ShieldAlert className="w-6 h-6" />
            )}
          </div>
          <div>
            <span className="text-[10px] font-bold tracking-wider text-slate-400 uppercase">
              Verifikasi Integritas Sistem
            </span>
            <h2 className="text-base sm:text-lg font-extrabold text-white flex items-center gap-2">
              <span>{isSystemVerified ? 'TERVERIFIKASI' : 'TIDAK TERVERIFIKASI'}</span>
              <span
                className={`text-xs px-2 py-0.5 rounded-full font-mono font-semibold ${
                  isSystemVerified
                    ? 'bg-emerald-500/20 text-emerald-300'
                    : 'bg-amber-500/20 text-amber-300'
                }`}
              >
                Zero-Dummy Mode
              </span>
            </h2>
          </div>
        </div>

        <button
          onClick={onRefreshSatellite}
          disabled={isRefreshing || cooldownSeconds > 0}
          className="w-full sm:w-auto px-4 py-2.5 bg-blue-600 hover:bg-blue-500 disabled:bg-slate-800 disabled:text-slate-500 text-white text-xs font-bold rounded-xl transition shadow-md flex items-center justify-center gap-2"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin' : ''}`} />
          <span>
            {isRefreshing
              ? 'Memverifikasi...'
              : cooldownSeconds > 0
              ? `Tunggu Cooldown (${cooldownSeconds}s)`
              : 'Perbarui Data Satelit'}
          </span>
        </button>
      </div>

      {/* 2. Main Fire Detection Card */}
      <div className="bg-[#111927] border-2 border-orange-500/40 rounded-2xl p-5 sm:p-6 shadow-xl relative overflow-hidden">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-full bg-orange-500/20 text-orange-500 flex items-center justify-center">
              <Flame className="w-5 h-5" />
            </div>
            <h3 className="text-base sm:text-lg font-extrabold text-white">
              Titik Panas Terdeteksi
            </h3>
          </div>
          <span
            className={`text-xs px-2.5 py-1 rounded-full font-bold uppercase tracking-wide ${
              isSystemVerified
                ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                : 'bg-slate-800 text-slate-400'
            }`}
          >
            {isSystemVerified ? 'Terverifikasi' : 'Tidak Terverifikasi'}
          </span>
        </div>

        {/* Big Counter */}
        <div className="my-5 flex items-baseline gap-3">
          <span className="text-5xl sm:text-6xl font-black font-mono tracking-tight text-orange-500">
            {fireCountDisplay}
          </span>
          <span className="text-sm font-bold text-slate-400">
            {isSystemVerified ? 'titik panas valid' : 'titik panas (belum diverifikasi)'}
          </span>
        </div>

        <p className="text-xs text-slate-400 leading-relaxed mb-4">
          Data berdasarkan hasil deteksi sensor satelit Near Real-Time (NRT) resmi NASA FIRMS di wilayah Hardi Mantangai.
        </p>

        <div className="border-t border-slate-800 pt-3.5 space-y-2 text-xs">
          <div className="flex items-center justify-between">
            <span className="text-slate-400">Waktu akuisisi satelit:</span>
            <span className="font-semibold text-slate-200 font-mono">
              {lastUpdateDisplay}
            </span>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-slate-400">Satelit aktif:</span>
            <span className="font-semibold text-slate-200">{satelliteDisplay}</span>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-slate-400">Status data:</span>
            <span className="font-semibold text-emerald-400">{statusText}</span>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-slate-400">Usia data:</span>
            <span className="font-semibold text-slate-200">{dataAgeDisplay}</span>
          </div>
        </div>

        {/* Action Buttons in Fire Card */}
        <div className="mt-5 grid grid-cols-2 gap-2.5">
          <button
            onClick={onNavigateToMap}
            className="px-4 py-3 bg-orange-600 hover:bg-orange-500 text-white text-xs sm:text-sm font-bold rounded-xl transition flex items-center justify-center gap-2 shadow-lg shadow-orange-600/20"
          >
            <Map className="w-4 h-4" />
            <span>Lihat di Peta</span>
          </button>
          <button
            onClick={onNavigateToList}
            className="px-4 py-3 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs sm:text-sm font-bold rounded-xl transition flex items-center justify-center gap-2 border border-slate-700"
          >
            <List className="w-4 h-4 text-orange-400" />
            <span>Daftar Titik Panas</span>
          </button>
        </div>
      </div>

      {/* 3. Wilayah Pantauan Hardi Mantangai & GPS Card */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Mantangai Region Reference */}
        <div className="bg-[#111927] border border-slate-800 rounded-2xl p-4 sm:p-5 shadow-lg space-y-3">
          <div className="flex items-center gap-2.5">
            <MapPin className="w-5 h-5 text-sky-400" />
            <h4 className="text-sm font-bold text-white uppercase tracking-wide">
              Wilayah Pantauan (Hardi Mantangai)
            </h4>
          </div>
          <p className="text-xs text-slate-300 font-medium leading-relaxed">
            {MANTHANGAI_CENTER.name}
          </p>

          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-3 space-y-1.5 text-xs font-mono">
            <div className="flex justify-between text-slate-400">
              <span>Koordinat Titik Tengah:</span>
              <span className="text-slate-200 font-bold">
                {MANTHANGAI_CENTER.latitude.toFixed(4)}°, {MANTHANGAI_CENTER.longitude.toFixed(4)}°
              </span>
            </div>
            <div className="flex justify-between text-slate-400">
              <span>BBox Referensi:</span>
              <span className="text-sky-300">{DEFAULT_MANTHANGAI_BBOX}</span>
            </div>
          </div>
        </div>

        {/* Real Device GPS Location Card */}
        <div className="bg-[#111927] border border-slate-800 rounded-2xl p-4 sm:p-5 shadow-lg space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <Compass className="w-5 h-5 text-emerald-400" />
              <h4 className="text-sm font-bold text-white uppercase tracking-wide">
                Lokasi Riil Perangkat (GPS)
              </h4>
            </div>
            <span
              className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                userLocation
                  ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                  : 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
              }`}
            >
              {userLocation ? 'GPS AKTIF' : 'BELUM AKTIF'}
            </span>
          </div>

          {userLocation ? (
            <div className="space-y-2 text-xs">
              <div className="flex justify-between">
                <span className="text-slate-400">Posisi:</span>
                <span className="font-mono font-bold text-slate-200">
                  {userLocation.latitude.toFixed(5)}°, {userLocation.longitude.toFixed(5)}°
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Akurasi:</span>
                <span className="font-mono text-emerald-400">
                  ±{userLocation.accuracyMeters ? Math.round(userLocation.accuracyMeters) : 10} meter
                </span>
              </div>
            </div>
          ) : (
            <p className="text-xs text-slate-400">
              {locationStatus === LocationStatus.LOCATION_PERMISSION_REQUIRED
                ? 'Izin akses GPS peramban diperlukan untuk mengukur jarak ke titik api terdekat.'
                : locationStatus === LocationStatus.LOCATION_LOADING
                ? 'Sedang membaca sinyal sensor GPS peramban...'
                : 'Aktifkan GPS untuk mengetahui posisi Anda terhadap titik api terdekat.'}
            </p>
          )}

          <button
            onClick={onRequestLocation}
            className="w-full py-2 bg-slate-900 hover:bg-slate-800 border border-slate-700 text-xs font-semibold text-slate-200 rounded-xl flex items-center justify-center gap-1.5 transition"
          >
            <Compass className="w-3.5 h-3.5 text-emerald-400" />
            <span>{userLocation ? 'Perbarui Lokasi GPS' : 'Aktifkan Sensor GPS'}</span>
          </button>
        </div>
      </div>

      {/* 4. Satellite Telemetry & MAP_KEY Configuration Card */}
      <div className="bg-[#111927] border border-slate-800 rounded-2xl p-4 sm:p-5 shadow-lg space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <Satellite className="w-5 h-5 text-amber-400" />
            <h4 className="text-sm font-bold text-white uppercase tracking-wide">
              Data Satelit & Telemetri Bukti (Audit Trail)
            </h4>
          </div>
          <button
            onClick={onOpenMapKeyModal}
            className="px-3 py-1 bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/30 text-xs font-semibold rounded-lg flex items-center gap-1.5 transition"
          >
            <Key className="w-3 h-3" />
            <span>Atur MAP_KEY</span>
          </button>
        </div>

        <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-3 space-y-1.5 text-xs font-mono">
          <div className="flex justify-between">
            <span className="text-slate-400">Sensor Prioritas:</span>
            <span className="text-slate-200">VIIRS NOAA-21 &rarr; NOAA-20 &rarr; SNPP &rarr; MODIS</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-400">Sensor Aktif:</span>
            <span className="text-amber-400 font-bold">{activeSensor || 'VIIRS_NOAA21_NRT'}</span>
          </div>
          {responseSha256Hash && (
            <div className="flex justify-between truncate">
              <span className="text-slate-400">SHA-256 Hash Bukti:</span>
              <span className="text-emerald-400 truncate max-w-[200px] sm:max-w-xs">
                {responseSha256Hash}
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
