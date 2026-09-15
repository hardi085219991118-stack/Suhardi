import React, { useState, useMemo } from 'react';
import {
  Flame,
  Filter,
  ArrowUpDown,
  Share2,
  Navigation,
  RotateCcw,
  Compass,
} from 'lucide-react';
import {
  FireDataRecord,
  LocationData,
  HotspotFilterCriteria,
  HotspotSortOrder,
} from '../types';
import {
  calculateDistanceKm,
  formatDistance,
  generateHotspotShareText,
  openWhatsAppShare,
} from '../utils/geo';

interface HotspotsListScreenProps {
  records: FireDataRecord[];
  allRecordsCount: number;
  userLocation: LocationData | null;
  filterCriteria: HotspotFilterCriteria;
  onOpenFilter: () => void;
  onResetFilter: () => void;
  onSelectRecord: (record: FireDataRecord) => void;
}

export const HotspotsListScreen: React.FC<HotspotsListScreenProps> = ({
  records,
  allRecordsCount,
  userLocation,
  filterCriteria,
  onOpenFilter,
  onResetFilter,
  onSelectRecord,
}) => {
  const [sortOrder, setSortOrder] = useState<HotspotSortOrder>(HotspotSortOrder.TERBARU);
  const [showSortMenu, setShowSortMenu] = useState(false);

  const isFiltered =
    filterCriteria.maxDistanceKm !== null && filterCriteria.maxDistanceKm !== undefined ||
    Boolean(filterCriteria.satellite) ||
    filterCriteria.maxAgeHours !== null && filterCriteria.maxAgeHours !== undefined;

  // Sort logic
  const sortedRecords = useMemo(() => {
    const list = [...records];
    if (sortOrder === HotspotSortOrder.TERBARU) {
      return list.sort((a, b) => (b.acquisitionTimestampMillis || 0) - (a.acquisitionTimestampMillis || 0));
    }
    if (sortOrder === HotspotSortOrder.TERDEKAT && userLocation) {
      return list.sort((a, b) => {
        const distA = calculateDistanceKm(userLocation.latitude, userLocation.longitude, a.latitude, a.longitude);
        const distB = calculateDistanceKm(userLocation.latitude, userLocation.longitude, b.latitude, b.longitude);
        return distA - distB;
      });
    }
    if (sortOrder === HotspotSortOrder.FRP_TERTINGGI) {
      return list.sort((a, b) => (b.frp || 0) - (a.frp || 0));
    }
    return list;
  }, [records, sortOrder, userLocation]);

  return (
    <div className="w-full max-w-4xl mx-auto px-4 py-5 pb-24 md:pb-12 space-y-4">
      {/* Title & Filter bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-[#111927] border border-slate-800 rounded-2xl p-4 sm:p-5 shadow-lg">
        <div>
          <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
            <Flame className="w-5 h-5 text-orange-500" />
            <span>Daftar Titik Panas</span>
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            {isFiltered
              ? `Menampilkan ${sortedRecords.length} dari ${allRecordsCount} titik panas`
              : `Total ${sortedRecords.length} titik panas terdeteksi`}
          </p>
        </div>

        <div className="flex items-center gap-2">
          {/* Sort Dropdown */}
          <div className="relative">
            <button
              onClick={() => setShowSortMenu(!showSortMenu)}
              className="px-3 py-2 rounded-xl bg-slate-900 border border-slate-700 text-xs font-semibold text-orange-400 hover:border-slate-600 flex items-center gap-1.5 transition"
            >
              <ArrowUpDown className="w-3.5 h-3.5" />
              <span>
                {sortOrder === HotspotSortOrder.TERBARU
                  ? 'Urutkan: Terbaru'
                  : sortOrder === HotspotSortOrder.TERDEKAT
                  ? 'Urutkan: Terdekat'
                  : 'Urutkan: FRP Tertinggi'}
              </span>
            </button>

            {showSortMenu && (
              <div className="absolute right-0 mt-1.5 w-44 bg-[#162032] border border-slate-700 rounded-xl shadow-2xl z-30 py-1 text-xs">
                <button
                  onClick={() => {
                    setSortOrder(HotspotSortOrder.TERBARU);
                    setShowSortMenu(false);
                  }}
                  className={`w-full text-left px-3.5 py-2 hover:bg-slate-800 transition ${
                    sortOrder === HotspotSortOrder.TERBARU ? 'text-orange-400 font-bold' : 'text-slate-300'
                  }`}
                >
                  Terbaru
                </button>
                <button
                  onClick={() => {
                    setSortOrder(HotspotSortOrder.TERDEKAT);
                    setShowSortMenu(false);
                  }}
                  disabled={!userLocation}
                  className={`w-full text-left px-3.5 py-2 hover:bg-slate-800 transition ${
                    !userLocation ? 'text-slate-600 cursor-not-allowed' : sortOrder === HotspotSortOrder.TERDEKAT ? 'text-orange-400 font-bold' : 'text-slate-300'
                  }`}
                >
                  Terdekat {!userLocation && '(Butuh GPS)'}
                </button>
                <button
                  onClick={() => {
                    setSortOrder(HotspotSortOrder.FRP_TERTINGGI);
                    setShowSortMenu(false);
                  }}
                  className={`w-full text-left px-3.5 py-2 hover:bg-slate-800 transition ${
                    sortOrder === HotspotSortOrder.FRP_TERTINGGI ? 'text-orange-400 font-bold' : 'text-slate-300'
                  }`}
                >
                  FRP Tertinggi
                </button>
              </div>
            )}
          </div>

          {/* Filter Button */}
          <button
            onClick={onOpenFilter}
            className={`p-2 rounded-xl border flex items-center justify-center transition ${
              isFiltered
                ? 'bg-orange-500/20 border-orange-500/50 text-orange-400'
                : 'bg-slate-900 border-slate-700 text-slate-300 hover:border-slate-600'
            }`}
            title="Saring Titik Panas"
          >
            <Filter className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Active Filter Pills */}
      {isFiltered && (
        <div className="flex items-center justify-between bg-orange-500/10 border border-orange-500/30 rounded-xl px-3.5 py-2 text-xs text-orange-300">
          <span>Penyaring sedang aktif</span>
          <button
            onClick={onResetFilter}
            className="flex items-center gap-1 text-red-400 hover:underline font-semibold"
          >
            <RotateCcw className="w-3 h-3" />
            <span>Atur Ulang Penyaring</span>
          </button>
        </div>
      )}

      {/* Hotspots List */}
      {sortedRecords.length === 0 ? (
        <div className="bg-[#111927] border border-slate-800 rounded-2xl p-8 sm:p-12 text-center flex flex-col items-center justify-center">
          <div className="w-16 h-16 rounded-full bg-slate-800/70 flex items-center justify-center text-slate-600 mb-4">
            <Flame className="w-8 h-8" />
          </div>
          <h3 className="text-base font-bold text-slate-300 mb-1">
            {allRecordsCount === 0
              ? 'Belum ada titik panas yang terdeteksi.'
              : 'Tidak ada titik panas yang sesuai dengan penyaring.'}
          </h3>
          <p className="text-xs text-slate-500 max-w-sm mb-4">
            {allRecordsCount === 0
              ? 'Data NASA FIRMS NRT menunjukkan tidak ada anomali termal di wilayah Hardi Mantangai.'
              : 'Coba ubah parameter jarak, sensor satelit, atau batas usia pada menu penyaring.'}
          </p>
          {isFiltered && (
            <button
              onClick={onResetFilter}
              className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold rounded-xl border border-slate-700 transition"
            >
              Atur Ulang Penyaring
            </button>
          )}
        </div>
      ) : (
        <div className="space-y-2.5">
          {sortedRecords.map((record, idx) => {
            const distanceKm = userLocation
              ? calculateDistanceKm(
                  userLocation.latitude,
                  userLocation.longitude,
                  record.latitude,
                  record.longitude
                )
              : null;

            return (
              <div
                key={`${record.latitude}-${record.longitude}-${record.acqDate}-${record.acqTime}-${idx}`}
                onClick={() => onSelectRecord(record)}
                className="group bg-[#111927] hover:bg-[#152033] border border-slate-800 hover:border-slate-700 rounded-xl p-3.5 sm:p-4 cursor-pointer transition flex items-center justify-between gap-3 shadow-sm hover:shadow-md"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-10 h-10 rounded-full bg-orange-500/15 text-orange-400 flex items-center justify-center shrink-0 border border-orange-500/25 group-hover:scale-105 transition">
                    <Flame className="w-5 h-5" />
                  </div>

                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-[11px] font-bold text-sky-400 font-mono">
                        {record.acqDate} {record.acqTime || '--'} UTC
                      </span>
                      {distanceKm !== null && (
                        <span className="text-[11px] font-bold text-emerald-400 flex items-center gap-0.5">
                          <Compass className="w-3 h-3" />
                          {formatDistance(distanceKm)}
                        </span>
                      )}
                    </div>

                    <div className="text-sm font-bold font-mono text-slate-100 mt-0.5 truncate">
                      {record.latitude.toFixed(5)}°, {record.longitude.toFixed(5)}°
                    </div>

                    <div className="flex items-center gap-2 text-[11px] text-slate-400 mt-1 flex-wrap">
                      <span>{record.satellite}</span>
                      <span>&bull;</span>
                      <span className="text-amber-400 font-semibold font-mono">
                        FRP {record.frp !== null && record.frp !== undefined ? `${record.frp.toFixed(1)} MW` : '-'}
                      </span>
                      <span>&bull;</span>
                      <span>Keyakinan {record.confidence || '-'}</span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-1 shrink-0">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      const text = generateHotspotShareText(record, distanceKm);
                      openWhatsAppShare(text);
                    }}
                    className="p-2 rounded-lg text-slate-400 hover:text-emerald-400 hover:bg-slate-800/80 transition"
                    title="Bagikan ke WhatsApp"
                  >
                    <Share2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
