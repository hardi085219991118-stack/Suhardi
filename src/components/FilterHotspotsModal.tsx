import React, { useState } from 'react';
import { X, Filter, RotateCcw, Check } from 'lucide-react';
import { HotspotFilterCriteria } from '../types';

interface FilterHotspotsModalProps {
  criteria: HotspotFilterCriteria;
  onApply: (criteria: HotspotFilterCriteria) => void;
  onReset: () => void;
  onClose: () => void;
  hasDeviceLocation: boolean;
}

export const FilterHotspotsModal: React.FC<FilterHotspotsModalProps> = ({
  criteria,
  onApply,
  onReset,
  onClose,
  hasDeviceLocation,
}) => {
  const [distance, setDistance] = useState<number | null>(criteria.maxDistanceKm ?? null);
  const [satellite, setSatellite] = useState<string | null>(criteria.satellite ?? null);
  const [ageHours, setAgeHours] = useState<number | null>(criteria.maxAgeHours ?? null);

  const distanceOptions = [
    { label: 'Semua Jarak', value: null },
    { label: '≤ 5 km', value: 5 },
    { label: '≤ 10 km', value: 10 },
    { label: '≤ 25 km', value: 25 },
    { label: '≤ 50 km', value: 50 },
    { label: '≤ 100 km', value: 100 },
  ];

  const satelliteOptions = [
    { label: 'Semua Satelit', value: null },
    { label: 'NOAA-21 (VIIRS)', value: 'NOAA-21' },
    { label: 'NOAA-20 (VIIRS)', value: 'NOAA-20' },
    { label: 'Suomi-NPP (VIIRS)', value: 'Suomi-NPP' },
    { label: 'MODIS (Terra/Aqua)', value: 'MODIS' },
  ];

  const ageOptions = [
    { label: 'Semua Waktu', value: null },
    { label: '≤ 6 Jam', value: 6 },
    { label: '≤ 12 Jam', value: 12 },
    { label: '≤ 24 Jam', value: 24 },
    { label: '≤ 48 Jam', value: 48 },
  ];

  const handleApply = () => {
    onApply({
      maxDistanceKm: distance,
      satellite,
      maxAgeHours: ageHours,
    });
    onClose();
  };

  const handleReset = () => {
    setDistance(null);
    setSatellite(null);
    setAgeHours(null);
    onReset();
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-md bg-[#111927] border border-slate-700 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-slate-800 bg-[#162032]">
          <div className="flex items-center gap-2.5">
            <Filter className="w-5 h-5 text-orange-400" />
            <h3 className="text-base font-bold text-white">Saring Titik Panas</h3>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-5 space-y-5 overflow-y-auto">
          {/* Filter 1: Jarak */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-xs font-bold uppercase tracking-wider text-slate-300">
                Radius Jarak dari Anda
              </label>
              {!hasDeviceLocation && (
                <span className="text-[10px] text-amber-400 bg-amber-400/10 px-2 py-0.5 rounded">
                  GPS belum aktif
                </span>
              )}
            </div>
            <div className="grid grid-cols-3 gap-2">
              {distanceOptions.map((opt) => {
                const isSelected = distance === opt.value;
                const isDisabled = opt.value !== null && !hasDeviceLocation;
                return (
                  <button
                    key={opt.label}
                    disabled={isDisabled}
                    onClick={() => setDistance(opt.value)}
                    className={`px-3 py-2 rounded-xl text-xs font-semibold transition border ${
                      isSelected
                        ? 'bg-orange-500 text-white border-orange-500 shadow-md shadow-orange-500/20'
                        : isDisabled
                        ? 'bg-slate-900/40 text-slate-600 border-slate-800 cursor-not-allowed'
                        : 'bg-slate-900 text-slate-300 border-slate-800 hover:border-slate-700'
                    }`}
                  >
                    {opt.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Filter 2: Satelit */}
          <div>
            <label className="text-xs font-bold uppercase tracking-wider text-slate-300 block mb-2">
              Sensor Satelit
            </label>
            <div className="grid grid-cols-2 gap-2">
              {satelliteOptions.map((opt) => {
                const isSelected = satellite === opt.value;
                return (
                  <button
                    key={opt.label}
                    onClick={() => setSatellite(opt.value)}
                    className={`px-3 py-2 rounded-xl text-xs font-semibold transition border text-left flex items-center justify-between ${
                      isSelected
                        ? 'bg-orange-500 text-white border-orange-500 shadow-md shadow-orange-500/20'
                        : 'bg-slate-900 text-slate-300 border-slate-800 hover:border-slate-700'
                    }`}
                  >
                    <span>{opt.label}</span>
                    {isSelected && <Check className="w-3.5 h-3.5 ml-1" />}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Filter 3: Usia Data */}
          <div>
            <label className="text-xs font-bold uppercase tracking-wider text-slate-300 block mb-2">
              Usia Maksimum Pengamatan
            </label>
            <div className="grid grid-cols-3 gap-2">
              {ageOptions.map((opt) => {
                const isSelected = ageHours === opt.value;
                return (
                  <button
                    key={opt.label}
                    onClick={() => setAgeHours(opt.value)}
                    className={`px-3 py-2 rounded-xl text-xs font-semibold transition border ${
                      isSelected
                        ? 'bg-orange-500 text-white border-orange-500 shadow-md shadow-orange-500/20'
                        : 'bg-slate-900 text-slate-300 border-slate-800 hover:border-slate-700'
                    }`}
                  >
                    {opt.label}
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-slate-800 bg-[#162032] flex gap-2.5">
          <button
            onClick={handleReset}
            className="px-4 py-2.5 rounded-xl border border-slate-700 text-slate-300 text-xs font-semibold hover:bg-slate-800 flex items-center gap-1.5 transition"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Atur Ulang</span>
          </button>
          <button
            onClick={handleApply}
            className="flex-1 px-4 py-2.5 bg-orange-600 hover:bg-orange-500 text-white text-xs font-bold rounded-xl shadow-lg shadow-orange-600/20 transition flex items-center justify-center gap-2"
          >
            <span>Terapkan Penyaring</span>
          </button>
        </div>
      </div>
    </div>
  );
};
