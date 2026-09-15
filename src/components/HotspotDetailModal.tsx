import React from 'react';
import { X, Flame, MapPin, Navigation, Share2, Compass, Satellite, Clock } from 'lucide-react';
import { FireDataRecord, LocationData } from '../types';
import {
  calculateDistanceKm,
  formatDistance,
  generateHotspotShareText,
  getGoogleMapsDirUrl,
  openWhatsAppShare,
} from '../utils/geo';

interface HotspotDetailModalProps {
  record: FireDataRecord | null;
  userLocation: LocationData | null;
  onClose: () => void;
  onViewOnMap?: (record: FireDataRecord) => void;
}

export const HotspotDetailModal: React.FC<HotspotDetailModalProps> = ({
  record,
  userLocation,
  onClose,
  onViewOnMap,
}) => {
  if (!record) return null;

  const distanceKm = userLocation
    ? calculateDistanceKm(
        userLocation.latitude,
        userLocation.longitude,
        record.latitude,
        record.longitude
      )
    : null;

  const handleShare = () => {
    const text = generateHotspotShareText(record, distanceKm);
    openWhatsAppShare(text);
  };

  const handleOpenGoogleMaps = () => {
    const url = getGoogleMapsDirUrl(record.latitude, record.longitude);
    window.open(url, '_blank');
  };

  // Convert UTC date time to local Indonesian WIB (UTC+7)
  let localTimeStr = '--';
  if (record.acquisitionTimestampMillis) {
    localTimeStr = new Date(record.acquisitionTimestampMillis).toLocaleString('id-ID', {
      timeZone: 'Asia/Jakarta',
      dateStyle: 'medium',
      timeStyle: 'medium',
    }) + ' WIB';
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-lg bg-[#111927] border border-orange-500/30 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between p-4 sm:p-5 border-b border-slate-800 bg-[#162032]">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-orange-500/20 text-orange-400 flex items-center justify-center border border-orange-500/30">
              <Flame className="w-6 h-6" />
            </div>
            <div>
              <h3 className="text-base sm:text-lg font-bold text-white">
                Detail Titik Panas (Hotspot)
              </h3>
              <p className="text-xs text-slate-400 font-mono">
                {record.satellite} &bull; {record.instrument}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body Content */}
        <div className="p-4 sm:p-5 space-y-4 overflow-y-auto">
          {/* Main Coordinate & Distance Card */}
          <div className="bg-[#0b1119] border border-slate-800 rounded-xl p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block">
                Koordinat Geografis
              </span>
              <span className="text-lg font-mono font-extrabold text-white tracking-tight">
                {record.latitude.toFixed(5)}°, {record.longitude.toFixed(5)}°
              </span>
            </div>
            {distanceKm !== null && (
              <div className="sm:text-right">
                <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block">
                  Jarak dari Posisi Anda
                </span>
                <span className="text-base font-bold text-emerald-400 flex items-center sm:justify-end gap-1">
                  <Compass className="w-4 h-4" />
                  {formatDistance(distanceKm)}
                </span>
              </div>
            )}
          </div>

          {/* Metric Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5 text-xs">
            <div className="bg-slate-900/80 border border-slate-800 rounded-lg p-3">
              <span className="text-slate-400 block mb-1">Radiative Power (FRP)</span>
              <span className="text-base font-bold text-orange-400 font-mono">
                {record.frp !== null && record.frp !== undefined ? `${record.frp.toFixed(1)} MW` : '-'}
              </span>
            </div>

            <div className="bg-slate-900/80 border border-slate-800 rounded-lg p-3">
              <span className="text-slate-400 block mb-1">Tingkat Keyakinan</span>
              <span className="text-base font-bold text-amber-300 font-mono">
                {record.confidence || 'Nominal'}
              </span>
            </div>

            <div className="bg-slate-900/80 border border-slate-800 rounded-lg p-3 col-span-2 sm:col-span-1">
              <span className="text-slate-400 block mb-1">Suhu Kecerahan</span>
              <span className="text-base font-bold text-yellow-400 font-mono">
                {record.brightness ? `${record.brightness} K` : '-'}
              </span>
            </div>

            <div className="bg-slate-900/80 border border-slate-800 rounded-lg p-3">
              <span className="text-slate-400 block mb-1">Waktu Melintas</span>
              <span className="text-sm font-semibold text-slate-200">
                {record.daynight === 'D' ? '☀️ Siang Hari' : '🌙 Malam Hari'}
              </span>
            </div>

            <div className="bg-slate-900/80 border border-slate-800 rounded-lg p-3">
              <span className="text-slate-400 block mb-1">Resolusi Scan/Track</span>
              <span className="text-sm font-semibold text-slate-200 font-mono">
                {record.scan || '-'} / {record.track || '-'}
              </span>
            </div>

            <div className="bg-slate-900/80 border border-slate-800 rounded-lg p-3 col-span-2 sm:col-span-1">
              <span className="text-slate-400 block mb-1">Versi Algoritma</span>
              <span className="text-sm font-semibold text-slate-200 font-mono">
                {record.version || 'NRT 2.0'}
              </span>
            </div>
          </div>

          {/* Time Card */}
          <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-3.5 space-y-2">
            <div className="flex items-center gap-2 text-slate-300 text-xs font-semibold">
              <Clock className="w-4 h-4 text-sky-400" />
              <span>Waktu Pengamatan Satelit</span>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
              <div>
                <span className="text-slate-400 block text-[11px]">Waktu UTC (Satelit):</span>
                <span className="font-mono text-slate-200 font-medium">
                  {record.acqDate} {record.acqTime || '--'} UTC
                </span>
              </div>
              <div>
                <span className="text-slate-400 block text-[11px]">Waktu Lokal (WIB):</span>
                <span className="font-mono text-sky-300 font-semibold">{localTimeStr}</span>
              </div>
            </div>
          </div>

          {/* Information Banner */}
          <div className="bg-orange-950/30 border border-orange-800/40 rounded-xl p-3 text-xs text-orange-200/90 leading-relaxed">
            <strong>Catatan Penting:</strong> Titik panas satelit adalah indikasi anomali suhu tinggi pada permukaan tanah. Pastikan dilakukan ground checking (verifikasi lapangan) sebelum mengambil tindakan teknis pemadaman.
          </div>
        </div>

        {/* Action Buttons */}
        <div className="p-4 sm:p-5 border-t border-slate-800 bg-[#162032] flex flex-col sm:flex-row gap-2.5">
          <button
            onClick={handleOpenGoogleMaps}
            className="flex-1 px-4 py-3 bg-blue-600 hover:bg-blue-500 text-white font-bold text-sm rounded-xl flex items-center justify-center gap-2 shadow-lg shadow-blue-600/20 transition active:scale-95"
          >
            <Navigation className="w-4 h-4" />
            <span>Navigasi (Google Maps)</span>
          </button>

          <button
            onClick={handleShare}
            className="flex-1 px-4 py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-sm rounded-xl flex items-center justify-center gap-2 shadow-lg shadow-emerald-600/20 transition active:scale-95"
          >
            <Share2 className="w-4 h-4" />
            <span>Bagikan ke WhatsApp</span>
          </button>

          {onViewOnMap && (
            <button
              onClick={() => {
                onViewOnMap(record);
                onClose();
              }}
              className="px-4 py-3 bg-slate-800 hover:bg-slate-700 text-slate-200 font-semibold text-sm rounded-xl flex items-center justify-center gap-2 border border-slate-700 transition"
            >
              <MapPin className="w-4 h-4 text-orange-400" />
              <span>Lihat di Peta</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
