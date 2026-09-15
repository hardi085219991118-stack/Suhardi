import React, { useState } from 'react';
import { X, Key, ExternalLink, ShieldCheck, Trash2, CheckCircle2, AlertTriangle } from 'lucide-react';
import { firmsApi } from '../services/firmsApi';

interface MapKeyModalProps {
  onClose: () => void;
  onSaved: () => void;
}

export const MapKeyModal: React.FC<MapKeyModalProps> = ({ onClose, onSaved }) => {
  const [mapKey, setMapKey] = useState(firmsApi.getStoredMapKey());
  const [savedSuccess, setSavedSuccess] = useState(false);

  const handleSave = () => {
    firmsApi.setStoredMapKey(mapKey.trim());
    setSavedSuccess(true);
    setTimeout(() => {
      onSaved();
      onClose();
    }, 600);
  };

  const handleClear = () => {
    firmsApi.setStoredMapKey('');
    setMapKey('');
    setSavedSuccess(true);
    setTimeout(() => {
      onSaved();
      onClose();
    }, 600);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-md bg-[#111927] border border-slate-700 rounded-2xl shadow-2xl overflow-hidden flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-slate-800 bg-[#162032]">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-amber-500/20 text-amber-400 flex items-center justify-center">
              <Key className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-white">Konfigurasi MAP_KEY NASA</h3>
              <p className="text-[11px] text-slate-400">Penyimpanan Kredensial Klien</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-5 space-y-4">
          <p className="text-xs text-slate-300 leading-relaxed">
            NASA FIRMS mewajibkan MAP_KEY (32 karakter alfanumerik) untuk mengakses data Near Real-Time (NRT) satelit secara resmi tanpa batasan demo.
          </p>

          <div>
            <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 block mb-1.5">
              MAP_KEY Pribadi Anda:
            </label>
            <div className="relative">
              <input
                type="text"
                value={mapKey}
                onChange={(e) => setMapKey(e.target.value)}
                placeholder="Contoh: 1a2b3c4d5e6f7g8h9i0j..."
                className="w-full bg-[#0b1119] border border-slate-700 focus:border-amber-500 rounded-xl px-3.5 py-2.5 text-xs text-white font-mono placeholder:text-slate-600 focus:outline-none transition"
              />
            </div>
          </div>

          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-3 space-y-2 text-[11px] text-slate-400">
            <div className="flex items-start gap-2">
              <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
              <span>
                Kunci Anda disimpan secara aman hanya di penyimpanan lokal peramban (localStorage) dan digunakan langsung untuk otentikasi ke layanan NASA FIRMS.
              </span>
            </div>

            <a
              href="https://firms.modaps.eosdis.nasa.gov/api/api_key/"
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 text-sky-400 hover:underline font-semibold pt-1"
            >
              <span>Dapatkan MAP_KEY Resmi Gratis dari NASA</span>
              <ExternalLink className="w-3 h-3" />
            </a>
          </div>

          {savedSuccess && (
            <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 rounded-xl flex items-center gap-2 text-xs text-emerald-300">
              <CheckCircle2 className="w-4 h-4" />
              <span>Konfigurasi MAP_KEY berhasil diperbarui!</span>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-slate-800 bg-[#162032] flex gap-2">
          {firmsApi.getStoredMapKey() && (
            <button
              onClick={handleClear}
              className="px-3.5 py-2.5 rounded-xl border border-red-500/40 text-red-400 text-xs font-semibold hover:bg-red-500/10 flex items-center gap-1.5 transition"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>Hapus</span>
            </button>
          )}
          <button
            onClick={handleSave}
            disabled={!mapKey.trim()}
            className="flex-1 px-4 py-2.5 bg-amber-600 hover:bg-amber-500 disabled:bg-slate-800 disabled:text-slate-500 text-white text-xs font-bold rounded-xl shadow-lg shadow-amber-600/20 transition flex items-center justify-center gap-2"
          >
            <span>Simpan MAP_KEY</span>
          </button>
        </div>
      </div>
    </div>
  );
};
