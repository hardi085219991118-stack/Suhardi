import React, { useState, useEffect, useRef, useCallback } from 'react';
import L from 'leaflet';
import {
  Ruler,
  Plus,
  Trash2,
  Share2,
  Download,
  MapPin,
  Compass,
  CheckCircle2,
  AlertTriangle,
  Layers,
  ArrowLeft,
  Calendar,
  FileText,
  Navigation as NavIcon,
  RotateCcw,
  Copy,
  Info,
  Check,
} from 'lucide-react';
import { LocationData, BaseMapLayer } from '../../types';
import {
  FieldPoint,
  LandMeasurement,
  MeasurementMethod,
} from '../../types/landMeasurement';
import {
  calculateGeodesicAreaSquareMeters,
  calculatePerimeterMeters,
  formatAreaUnits,
  formatDistanceIndonesian,
  formatIndonesianNumber,
  createLandMeasurement,
} from '../../services/landMeasurementEngine';
import { landMeasurementRepository } from '../../services/landMeasurementRepository';
import { MANTHANGAI_CENTER } from '../../utils/geo';

interface LandMeasurementScreenProps {
  userLocation: LocationData | null;
  onRequestLocation: () => void;
}

type ScreenMode = 'LIST' | 'SETUP' | 'ACTIVE_MEASURING' | 'DETAIL';

export const LandMeasurementScreen: React.FC<LandMeasurementScreenProps> = ({
  userLocation,
  onRequestLocation,
}) => {
  // Navigation & View State
  const [mode, setMode] = useState<ScreenMode>('LIST');
  const [savedMeasurements, setSavedMeasurements] = useState<LandMeasurement[]>([]);
  const [selectedMeasurement, setSelectedMeasurement] = useState<LandMeasurement | null>(null);

  // New Measurement Setup State
  const [measurementName, setMeasurementName] = useState('');
  const [selectedMethod, setSelectedMethod] = useState<MeasurementMethod>('POLYGON_FIELD');
  const [measurementNotes, setMeasurementNotes] = useState('');

  // Active Measuring Session State
  const [activePoints, setActivePoints] = useState<FieldPoint[]>([]);
  const [activeLayer, setActiveLayer] = useState<BaseMapLayer>(BaseMapLayer.OPEN_STREET_MAP);
  const [showLayerModal, setShowLayerModal] = useState(false);
  const [isCopied, setIsCopied] = useState(false);
  const [notificationMsg, setNotificationMsg] = useState<string | null>(null);

  // Map References
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const layerGroupRef = useRef<L.LayerGroup | null>(null);
  const tileLayerRef = useRef<L.TileLayer | null>(null);

  // Load saved measurements on mount
  const loadSaved = useCallback(() => {
    const list = landMeasurementRepository.getAll();
    setSavedMeasurements(list);
  }, []);

  useEffect(() => {
    loadSaved();
  }, [loadSaved]);

  // Flash notification helper
  const showToast = (msg: string) => {
    setNotificationMsg(msg);
    setTimeout(() => setNotificationMsg(null), 3500);
  };

  // GPS Accuracy assessment
  const isGpsActive = Boolean(userLocation);
  const gpsAccuracy = userLocation?.accuracyMeters ? Math.round(userLocation.accuracyMeters) : null;
  const isGpsStable = isGpsActive && gpsAccuracy !== null && gpsAccuracy <= 15;

  // Initialize Map for ACTIVE_MEASURING or DETAIL
  useEffect(() => {
    if (mode !== 'ACTIVE_MEASURING' && mode !== 'DETAIL') {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
      return;
    }

    if (!mapContainerRef.current) return;

    // Clean up previous instance if any
    if (mapInstanceRef.current) {
      mapInstanceRef.current.remove();
      mapInstanceRef.current = null;
    }

    const initLat = userLocation?.latitude || MANTHANGAI_CENTER.latitude;
    const initLon = userLocation?.longitude || MANTHANGAI_CENTER.longitude;

    const map = L.map(mapContainerRef.current, {
      center: [initLat, initLon],
      zoom: 16,
      zoomControl: false,
      attributionControl: false,
    });

    const tileUrl =
      activeLayer === BaseMapLayer.SATELLITE_HYBRID
        ? 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'
        : 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

    const tile = L.tileLayer(tileUrl, { maxZoom: 19 }).addTo(map);
    tileLayerRef.current = tile;

    const group = L.layerGroup().addTo(map);
    layerGroupRef.current = group;
    mapInstanceRef.current = map;

    // Map tap handler to add points manually in ACTIVE_MEASURING mode
    if (mode === 'ACTIVE_MEASURING') {
      map.on('click', (e: L.LeafletMouseEvent) => {
        const { lat, lng } = e.latlng;
        addPointCoordinate(lat, lng, null, null);
      });
    }

    return () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    };
  }, [mode]);

  // Update Tile Layer when activeLayer changes
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (tileLayerRef.current) {
      map.removeLayer(tileLayerRef.current);
    }

    const tileUrl =
      activeLayer === BaseMapLayer.SATELLITE_HYBRID
        ? 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'
        : 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

    tileLayerRef.current = L.tileLayer(tileUrl, { maxZoom: 19 }).addTo(map);
  }, [activeLayer]);

  // Add Point helper
  const addPointCoordinate = (
    lat: number,
    lon: number,
    alt: number | null,
    accuracy: number | null
  ) => {
    setActivePoints((prev) => {
      const nextNum = prev.length + 1;
      const newPoint: FieldPoint = {
        id: `PT_${Date.now()}_${nextNum}`,
        pointNumber: nextNum,
        label: `P${nextNum}`,
        latitude: lat,
        longitude: lon,
        altitudeMeters: alt,
        accuracyMeters: accuracy,
        timestampMillis: Date.now(),
        orderIndex: prev.length,
      };
      return [...prev, newPoint];
    });
  };

  // Add current GPS Point
  const handleAddGpsPoint = () => {
    if (!userLocation) {
      onRequestLocation();
      showToast('Mengaktifkan sensor GPS...');
      return;
    }

    addPointCoordinate(
      userLocation.latitude,
      userLocation.longitude,
      null,
      userLocation.accuracyMeters || null
    );
    showToast(`Titik P${activePoints.length + 1} berhasil ditambahkan dari GPS.`);
  };

  // Remove last point
  const handleUndoLastPoint = () => {
    if (activePoints.length === 0) return;
    setActivePoints((prev) => prev.slice(0, -1));
  };

  // Clear all active points
  const handleResetPoints = () => {
    if (activePoints.length === 0) return;
    if (window.confirm('Hapus semua titik pengukuran saat ini?')) {
      setActivePoints([]);
    }
  };

  // Draw points, lines, and polygon on the map
  const renderMapLayers = useCallback(() => {
    const map = mapInstanceRef.current;
    const group = layerGroupRef.current;
    if (!map || !group) return;

    group.clearLayers();

    const pointsToRender = mode === 'DETAIL' && selectedMeasurement
      ? selectedMeasurement.points
      : activePoints;

    const isPolygonMode =
      (mode === 'DETAIL' && selectedMeasurement?.method === 'POLYGON_FIELD') ||
      (mode === 'ACTIVE_MEASURING' && selectedMethod === 'POLYGON_FIELD');

    const latLngs = pointsToRender.map((p) => [p.latitude, p.longitude] as [number, number]);

    // 1. Draw connecting polyline / polygon
    if (latLngs.length >= 2) {
      if (isPolygonMode && latLngs.length >= 3) {
        L.polygon(latLngs, {
          color: '#06b6d4',
          weight: 3,
          fillColor: '#06b6d4',
          fillOpacity: 0.25,
          dashArray: '6, 6',
        }).addTo(group);
      } else {
        L.polyline(latLngs, {
          color: '#0284c7',
          weight: 3,
          dashArray: '4, 4',
        }).addTo(group);
      }
    }

    // 2. Draw Measurement Point Markers (P1, P2, etc. - NEVER flame)
    pointsToRender.forEach((point) => {
      const pinIcon = L.divIcon({
        className: 'land-measurement-pin',
        html: `
          <div style="display:flex;flex-direction:column;align-items:center;cursor:pointer;">
            <div style="background:#0284c7;color:#ffffff;font-size:11px;font-weight:bold;padding:2px 6px;border-radius:6px;border:1.5px solid #ffffff;box-shadow:0 2px 6px rgba(0,0,0,0.6);white-space:nowrap;">
              ${point.label}
            </div>
            <div style="width:10px;height:10px;background:#06b6d4;border:2px solid #ffffff;border-radius:50%;margin-top:2px;box-shadow:0 0 6px #06b6d4;"></div>
          </div>
        `,
        iconSize: [40, 34],
        iconAnchor: [20, 34],
      });

      L.marker([point.latitude, point.longitude], { icon: pinIcon })
        .bindPopup(
          `<div style="font-size:12px;padding:4px;color:#111827;">
            <strong>Titik ${point.label}</strong><br/>
            Lat: ${point.latitude.toFixed(6)}°<br/>
            Lon: ${point.longitude.toFixed(6)}°<br/>
            Akurasi: ${point.accuracyMeters ? `±${Math.round(point.accuracyMeters)}m` : '-'}<br/>
            Waktu: ${new Date(point.timestampMillis).toLocaleTimeString('id-ID')}
          </div>`
        )
        .addTo(group);
    });

    // 3. Current GPS Location Marker
    if (userLocation) {
      const userGpsIcon = L.divIcon({
        className: 'user-gps-pin',
        html: `
          <div style="position:relative;width:22px;height:22px;display:flex;align-items:center;justify-content:center;">
            <div style="position:absolute;width:22px;height:22px;border-radius:50%;background:rgba(16,185,129,0.35);animation:ping 2s cubic-bezier(0,0,0.2,1) infinite;"></div>
            <div style="width:12px;height:12px;border-radius:50%;background:#10b981;border:2px solid #ffffff;box-shadow:0 0 6px #10b981;"></div>
          </div>
        `,
        iconSize: [22, 22],
        iconAnchor: [11, 11],
      });

      L.marker([userLocation.latitude, userLocation.longitude], { icon: userGpsIcon })
        .bindPopup(
          `<div style="font-size:12px;color:#111827;"><strong>Posisi Anda (GPS)</strong><br>Akurasi: ±${gpsAccuracy || 10} m</div>`
        )
        .addTo(group);
    }
  }, [mode, selectedMeasurement, activePoints, selectedMethod, userLocation, gpsAccuracy]);

  useEffect(() => {
    renderMapLayers();
  }, [renderMapLayers]);

  // Real-time calculations for active session
  const activePerimeter = calculatePerimeterMeters(
    activePoints,
    selectedMethod === 'POLYGON_FIELD'
  );
  const activeAreaSqM =
    selectedMethod === 'POLYGON_FIELD' && activePoints.length >= 3
      ? calculateGeodesicAreaSquareMeters(activePoints)
      : 0;
  const activeAreaFormatted = formatAreaUnits(activeAreaSqM);

  // Save measurement
  const handleSaveMeasurement = () => {
    if (activePoints.length < 2) {
      showToast('Minimal 2 titik diperlukan untuk menyimpan pengukuran.');
      return;
    }

    if (selectedMethod === 'POLYGON_FIELD' && activePoints.length < 3) {
      showToast('Minimal 3 titik diperlukan untuk menghitung luas bidang polygon.');
      return;
    }

    const newMeas = createLandMeasurement(
      measurementName,
      selectedMethod,
      activePoints,
      measurementNotes
    );

    landMeasurementRepository.save(newMeas);
    loadSaved();
    setSelectedMeasurement(newMeas);
    setMode('DETAIL');
    showToast('Pengukuran tanah berhasil disimpan!');
  };

  // Delete measurement
  const handleDeleteMeasurement = (id: string) => {
    if (window.confirm('Yakin ingin menghapus riwayat pengukuran ini?')) {
      landMeasurementRepository.delete(id);
      loadSaved();
      if (selectedMeasurement?.id === id) {
        setSelectedMeasurement(null);
        setMode('LIST');
      }
      showToast('Pengukuran dihapus.');
    }
  };

  // Export helpers
  const handleDownloadFile = (content: string, filename: string, mimeType: string) => {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    showToast(`File ${filename} berhasil diunduh.`);
  };

  const handleCopyText = (text: string) => {
    navigator.clipboard.writeText(text);
    setIsCopied(true);
    showToast('Teks berhasil disalin ke papan klip!');
    setTimeout(() => setIsCopied(false), 2000);
  };

  return (
    <div className="w-full min-h-[calc(100vh-120px)] md:min-h-[calc(100vh-64px)] pb-16 md:pb-6 bg-[#090e17] text-slate-100 flex flex-col">
      {/* Toast Notification */}
      {notificationMsg && (
        <div className="fixed top-16 left-1/2 -translate-x-1/2 z-50 bg-cyan-600 text-white px-4 py-2 rounded-xl text-xs font-semibold shadow-2xl flex items-center gap-2 border border-cyan-400/40 animate-fade-in">
          <CheckCircle2 className="w-4 h-4" />
          <span>{notificationMsg}</span>
        </div>
      )}

      {/* Layer Selection Modal - STRICTLY ONLY 2 OPTIONS */}
      {showLayerModal && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#111927] border border-slate-700 w-full max-w-sm rounded-2xl p-5 shadow-2xl">
            <h3 className="text-sm font-bold text-slate-100 mb-1 flex items-center gap-2">
              <Layers className="w-4 h-4 text-cyan-400" />
              PILIHAN LAYER PETA
            </h3>
            <p className="text-[11px] text-slate-400 mb-4">
              Pilih tampilan peta latar belakang untuk navigasi dan pengukuran:
            </p>

            <div className="space-y-2.5">
              {/* Option 1: Citra Satelit (Esri World Imagery) */}
              <button
                onClick={() => {
                  setActiveLayer(BaseMapLayer.SATELLITE_HYBRID);
                  setShowLayerModal(false);
                }}
                className={`w-full p-3 rounded-xl border text-left flex items-center justify-between transition ${
                  activeLayer === BaseMapLayer.SATELLITE_HYBRID
                    ? 'border-cyan-500 bg-cyan-500/10 text-cyan-300'
                    : 'border-slate-700 bg-slate-800/60 text-slate-200 hover:border-slate-600'
                }`}
              >
                <div>
                  <div className="text-xs font-bold">1. Citra Satelit (Esri World Imagery)</div>
                  <div className="text-[10px] text-slate-400 mt-0.5">
                    Citra satelit resolusi tinggi global
                  </div>
                </div>
                {activeLayer === BaseMapLayer.SATELLITE_HYBRID && (
                  <Check className="w-4 h-4 text-cyan-400" />
                )}
              </button>

              {/* Option 2: Peta Standar (OpenStreetMap) */}
              <button
                onClick={() => {
                  setActiveLayer(BaseMapLayer.OPEN_STREET_MAP);
                  setShowLayerModal(false);
                }}
                className={`w-full p-3 rounded-xl border text-left flex items-center justify-between transition ${
                  activeLayer === BaseMapLayer.OPEN_STREET_MAP
                    ? 'border-cyan-500 bg-cyan-500/10 text-cyan-300'
                    : 'border-slate-700 bg-slate-800/60 text-slate-200 hover:border-slate-600'
                }`}
              >
                <div>
                  <div className="text-xs font-bold">2. Peta Standar (OpenStreetMap)</div>
                  <div className="text-[10px] text-slate-400 mt-0.5">
                    Peta jalan, toponimi & batas wilayah
                  </div>
                </div>
                {activeLayer === BaseMapLayer.OPEN_STREET_MAP && (
                  <Check className="w-4 h-4 text-cyan-400" />
                )}
              </button>
            </div>

            <button
              onClick={() => setShowLayerModal(false)}
              className="w-full mt-4 py-2 bg-slate-800 hover:bg-slate-700 text-xs font-semibold rounded-xl text-slate-300 transition"
            >
              Tutup
            </button>
          </div>
        </div>
      )}

      {/* Main Container */}
      <div className="max-w-6xl w-full mx-auto p-3 sm:p-4 flex-1 flex flex-col">
        {/* Top Header & Segmented Nav */}
        <div className="bg-[#111927] border border-slate-800 rounded-2xl p-3.5 mb-3.5 shadow-lg flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-cyan-500/20 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <Ruler className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-extrabold tracking-wide uppercase text-slate-100">
                PENGUKURAN TANAH
              </h2>
              <p className="text-[10px] text-slate-400 font-mono">
                MODUL GEODETIK &bull; JARAK &bull; LUAS &bull; KELILING
              </p>
            </div>
          </div>

          {/* Navigation Action Buttons */}
          <div className="flex items-center gap-2">
            <button
              onClick={() => {
                setMeasurementName(`Bidang Tanah ${new Date().toLocaleDateString('id-ID')}`);
                setActivePoints([]);
                setMode('SETUP');
              }}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1.5 transition ${
                mode === 'SETUP' || mode === 'ACTIVE_MEASURING'
                  ? 'bg-cyan-500 text-slate-950 shadow-md'
                  : 'bg-cyan-500/15 text-cyan-400 border border-cyan-500/30 hover:bg-cyan-500/25'
              }`}
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Pengukuran Baru</span>
            </button>

            <button
              onClick={() => setMode('LIST')}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1.5 transition ${
                mode === 'LIST'
                  ? 'bg-slate-700 text-white'
                  : 'bg-slate-800/80 text-slate-400 border border-slate-700 hover:text-slate-200'
              }`}
            >
              <span>Tersimpan ({savedMeasurements.length})</span>
            </button>
          </div>
        </div>

        {/* GPS Live Telemetry Status Banner */}
        <div className="bg-[#0f172a] border border-slate-800 rounded-xl px-3.5 py-2.5 mb-3.5 flex flex-wrap items-center justify-between gap-2 text-xs">
          <div className="flex items-center gap-2">
            <div
              className={`w-2.5 h-2.5 rounded-full ${
                isGpsActive ? (isGpsStable ? 'bg-emerald-400' : 'bg-amber-400') : 'bg-rose-400'
              } animate-pulse`}
            />
            <span className="font-semibold text-slate-200">
              {isGpsActive
                ? isGpsStable
                  ? 'GPS AKTIF (Sinyal Stabil)'
                  : 'GPS BELUM STABIL (Menunggu Kalibrasi)'
                : 'GPS TIDAK AKTIF'}
            </span>
            {gpsAccuracy !== null && (
              <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                Akurasi: ±{gpsAccuracy} meter
              </span>
            )}
          </div>

          <button
            onClick={onRequestLocation}
            className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-[11px] text-cyan-300 border border-slate-700 flex items-center gap-1"
          >
            <Compass className="w-3 h-3 text-cyan-400" />
            <span>Kalibrasi GPS</span>
          </button>
        </div>

        {/* MODE: SETUP (Konfigurasi Pengukuran Baru) */}
        {mode === 'SETUP' && (
          <div className="bg-[#111927] border border-slate-800 rounded-2xl p-4 md:p-6 max-w-xl mx-auto w-full shadow-xl">
            <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
              <Plus className="w-4 h-4 text-cyan-400" />
              KONFIGURASI PENGUKURAN BARU
            </h3>

            <div className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                  Nama Pengukuran / Lahan
                </label>
                <input
                  type="text"
                  value={measurementName}
                  onChange={(e) => setMeasurementName(e.target.value)}
                  placeholder="Contoh: Lahan Blok Mantangai Hilir"
                  className="w-full bg-[#0d1520] border border-slate-700 focus:border-cyan-500 rounded-xl px-3.5 py-2 text-xs text-white outline-none transition"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                  Tanggal Pengukuran
                </label>
                <div className="text-xs text-slate-400 bg-[#0d1520] border border-slate-800 px-3.5 py-2 rounded-xl flex items-center gap-2">
                  <Calendar className="w-3.5 h-3.5 text-cyan-400" />
                  <span>{new Date().toLocaleDateString('id-ID', { dateStyle: 'full' })}</span>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-2">
                  Metode Pengukuran
                </label>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                  {[
                    {
                      id: 'POLYGON_FIELD',
                      title: 'Polygon / Bidang',
                      desc: 'Ukur batas keliling dan luas bidang tanah (Min 3 titik)',
                    },
                    {
                      id: 'POINT_TO_POINT',
                      title: 'Titik-ke-Titik',
                      desc: 'Ukur jarak linear antar patok patokan batas',
                    },
                    {
                      id: 'GPS_TRACK',
                      title: 'Jalur GPS',
                      desc: 'Ambil koordinat berjalan sepanjang perimeter',
                    },
                    {
                      id: 'MANUAL_POINTS',
                      title: 'Titik Manual',
                      desc: 'Menandai koordinat dengan mengetuk langsung pada peta',
                    },
                  ].map((m) => (
                    <button
                      key={m.id}
                      type="button"
                      onClick={() => setSelectedMethod(m.id as MeasurementMethod)}
                      className={`p-3 rounded-xl border text-left transition ${
                        selectedMethod === m.id
                          ? 'border-cyan-500 bg-cyan-500/10 text-cyan-200'
                          : 'border-slate-800 bg-[#0d1520] text-slate-300 hover:border-slate-700'
                      }`}
                    >
                      <div className="text-xs font-bold flex items-center gap-1.5">
                        <span
                          className={`w-2 h-2 rounded-full ${
                            selectedMethod === m.id ? 'bg-cyan-400' : 'bg-slate-600'
                          }`}
                        />
                        {m.title}
                      </div>
                      <div className="text-[10px] text-slate-400 mt-1">{m.desc}</div>
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                  Catatan / Keterangan (Opsional)
                </label>
                <textarea
                  value={measurementNotes}
                  onChange={(e) => setMeasurementNotes(e.target.value)}
                  placeholder="Misal: Kondisi batas parit, patok kayu ulin, patok beton..."
                  rows={2}
                  className="w-full bg-[#0d1520] border border-slate-700 focus:border-cyan-500 rounded-xl p-3 text-xs text-white outline-none resize-none"
                />
              </div>

              <div className="pt-2 flex items-center gap-2.5">
                <button
                  type="button"
                  onClick={() => setMode('ACTIVE_MEASURING')}
                  className="flex-1 py-2.5 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-extrabold text-xs flex items-center justify-center gap-2 shadow-lg transition active:scale-95"
                >
                  <NavIcon className="w-4 h-4" />
                  <span>MULAI UKUR</span>
                </button>

                <button
                  type="button"
                  onClick={() => setMode('LIST')}
                  className="px-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-300 transition"
                >
                  Batal
                </button>
              </div>
            </div>
          </div>
        )}

        {/* MODE: ACTIVE_MEASURING (Kanvas Peta & Pengambilan Titik) */}
        {mode === 'ACTIVE_MEASURING' && (
          <div className="flex-1 flex flex-col gap-3">
            {/* Real-time Telemetry Floating Bar */}
            <div className="bg-[#111927] border border-cyan-500/40 rounded-2xl p-3 shadow-xl grid grid-cols-2 sm:grid-cols-5 gap-2 text-center text-xs">
              <div className="bg-[#090e17] p-2 rounded-xl border border-slate-800">
                <span className="text-[10px] text-slate-400 block">Jumlah Titik</span>
                <span className="text-sm font-extrabold text-cyan-400 font-mono">
                  {activePoints.length}
                </span>
              </div>
              <div className="bg-[#090e17] p-2 rounded-xl border border-slate-800">
                <span className="text-[10px] text-slate-400 block">Jarak / Keliling</span>
                <span className="text-xs font-bold text-slate-200 font-mono">
                  {formatDistanceIndonesian(activePerimeter)}
                </span>
              </div>
              <div className="bg-[#090e17] p-2 rounded-xl border border-slate-800 col-span-2 sm:col-span-1">
                <span className="text-[10px] text-slate-400 block">Luas (m²)</span>
                <span className="text-xs font-bold text-emerald-400 font-mono">
                  {activePoints.length >= 3
                    ? activeAreaFormatted.squareMeters
                    : '--'}
                </span>
              </div>
              <div className="bg-[#090e17] p-2 rounded-xl border border-slate-800">
                <span className="text-[10px] text-slate-400 block">Luas (Hektare)</span>
                <span className="text-xs font-bold text-amber-400 font-mono">
                  {activePoints.length >= 3 ? activeAreaFormatted.hectares : '--'}
                </span>
              </div>
              <div className="bg-[#090e17] p-2 rounded-xl border border-slate-800">
                <span className="text-[10px] text-slate-400 block">Akurasi GPS</span>
                <span className="text-xs font-bold text-slate-300 font-mono">
                  {gpsAccuracy !== null ? `±${gpsAccuracy} m` : 'Off'}
                </span>
              </div>
            </div>

            {/* Notice if less than 3 points for polygon */}
            {selectedMethod === 'POLYGON_FIELD' && activePoints.length < 3 && (
              <div className="bg-amber-500/10 border border-amber-500/30 rounded-xl px-3 py-2 text-[11px] text-amber-300 flex items-center gap-2">
                <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0" />
                <span>Minimal 3 titik diperlukan untuk menghitung luas bidang polygon.</span>
              </div>
            )}

            {/* Interactive Leaflet Map Container */}
            <div className="relative w-full h-[400px] md:h-[480px] rounded-2xl overflow-hidden border border-slate-800 shadow-xl bg-[#0d1520]">
              <div ref={mapContainerRef} className="w-full h-full z-0" />

              {/* Floating Layer Switch Button */}
              <button
                onClick={() => setShowLayerModal(true)}
                className="absolute top-3 right-3 z-10 p-2.5 rounded-xl bg-[#111927]/90 backdrop-blur-md border border-slate-700 text-slate-200 shadow-xl flex items-center gap-1.5 text-xs font-semibold hover:border-slate-500 transition"
              >
                <Layers className="w-4 h-4 text-cyan-400" />
                <span className="hidden sm:inline">Layer Peta</span>
              </button>

              {/* Tap to add hint badge */}
              <div className="absolute top-3 left-3 z-10 bg-[#111927]/90 backdrop-blur-md border border-slate-700/80 rounded-xl px-3 py-1.5 text-[11px] text-slate-300">
                Ketuk peta atau gunakan tombol GPS di bawah untuk menambah titik batas.
              </div>
            </div>

            {/* Action Bar for Adding & Finalizing Points */}
            <div className="bg-[#111927] border border-slate-800 rounded-2xl p-3 flex flex-wrap items-center justify-between gap-2.5">
              <div className="flex flex-wrap items-center gap-2">
                <button
                  onClick={handleAddGpsPoint}
                  className="px-3.5 py-2 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs flex items-center gap-1.5 shadow-md active:scale-95 transition"
                >
                  <MapPin className="w-3.5 h-3.5" />
                  <span>+ Titik dari GPS</span>
                </button>

                <button
                  onClick={handleUndoLastPoint}
                  disabled={activePoints.length === 0}
                  className="px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-300 font-semibold text-xs flex items-center gap-1.5 transition"
                >
                  <RotateCcw className="w-3.5 h-3.5 text-amber-400" />
                  <span>Hapus Terakhir</span>
                </button>

                <button
                  onClick={handleResetPoints}
                  disabled={activePoints.length === 0}
                  className="px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-400 font-semibold text-xs flex items-center gap-1.5 transition"
                >
                  <Trash2 className="w-3.5 h-3.5 text-rose-400" />
                  <span>Reset</span>
                </button>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={handleSaveMeasurement}
                  disabled={activePoints.length < 2}
                  className="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 disabled:opacity-40 text-slate-950 font-extrabold text-xs flex items-center gap-1.5 shadow-md active:scale-95 transition"
                >
                  <CheckCircle2 className="w-4 h-4" />
                  <span>SELESAI & SIMPAN</span>
                </button>

                <button
                  onClick={() => {
                    if (activePoints.length === 0 || window.confirm('Batalkan sesi pengukuran?')) {
                      setMode('LIST');
                    }
                  }}
                  className="px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-300 transition"
                >
                  Batal
                </button>
              </div>
            </div>

            {/* List of active points preview table */}
            {activePoints.length > 0 && (
              <div className="bg-[#111927] border border-slate-800 rounded-2xl p-3 shadow-lg overflow-x-auto">
                <span className="text-xs font-bold text-slate-300 block mb-2">
                  Daftar Titik Koordinat ({activePoints.length})
                </span>
                <table className="w-full text-left text-[11px] text-slate-300 font-mono">
                  <thead>
                    <tr className="border-b border-slate-800 text-slate-400">
                      <th className="py-1 px-2">Patok</th>
                      <th className="py-1 px-2">Latitude</th>
                      <th className="py-1 px-2">Longitude</th>
                      <th className="py-1 px-2">Akurasi</th>
                      <th className="py-1 px-2">Waktu</th>
                    </tr>
                  </thead>
                  <tbody>
                    {activePoints.map((p) => (
                      <tr key={p.id} className="border-b border-slate-800/50 hover:bg-slate-800/40">
                        <td className="py-1 px-2 font-bold text-cyan-400">{p.label}</td>
                        <td className="py-1 px-2">{p.latitude.toFixed(6)}°</td>
                        <td className="py-1 px-2">{p.longitude.toFixed(6)}°</td>
                        <td className="py-1 px-2">
                          {p.accuracyMeters ? `±${Math.round(p.accuracyMeters)}m` : '-'}
                        </td>
                        <td className="py-1 px-2">
                          {new Date(p.timestampMillis).toLocaleTimeString('id-ID')}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* MODE: DETAIL (Tinjauan Rinci Pengukuran Tersimpan) */}
        {mode === 'DETAIL' && selectedMeasurement && (
          <div className="space-y-4">
            <div className="flex items-center justify-between bg-[#111927] border border-slate-800 rounded-2xl p-3.5 shadow-lg">
              <button
                onClick={() => setMode('LIST')}
                className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-300 flex items-center gap-1.5 transition"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>Kembali ke Riwayat</span>
              </button>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => {
                    const text = landMeasurementRepository.formatWhatsAppReport(selectedMeasurement);
                    handleCopyText(text);
                  }}
                  className="px-3 py-1.5 rounded-xl bg-emerald-500/15 border border-emerald-500/30 hover:bg-emerald-500/25 text-emerald-400 text-xs font-bold flex items-center gap-1.5 transition"
                >
                  <Share2 className="w-3.5 h-3.5" />
                  <span>Salin Laporan WA</span>
                </button>

                <button
                  onClick={() => handleDeleteMeasurement(selectedMeasurement.id)}
                  className="p-1.5 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 hover:bg-rose-500/20 transition"
                  title="Hapus"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Measurement Card Details */}
            <div className="bg-[#111927] border border-slate-800 rounded-2xl p-4 md:p-5 shadow-xl">
              <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-800 pb-4">
                <div>
                  <h3 className="text-base font-extrabold text-white">{selectedMeasurement.name}</h3>
                  <div className="text-xs text-slate-400 flex items-center gap-3 mt-1">
                    <span>{selectedMeasurement.date}</span>
                    <span>&bull;</span>
                    <span className="capitalize">
                      Metode: {selectedMeasurement.method === 'POLYGON_FIELD' ? 'Polygon Bidang' : 'Garis / Titik'}
                    </span>
                  </div>
                </div>

                {selectedMeasurement.gpsAccuracyMetersAvg && (
                  <div className="px-3 py-1.5 rounded-xl bg-cyan-500/10 border border-cyan-500/30 text-cyan-400 text-xs font-mono">
                    Akurasi Rata-rata: ±{selectedMeasurement.gpsAccuracyMetersAvg} m
                  </div>
                )}
              </div>

              {/* Geodesic Calculation Results Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 my-4">
                <div className="bg-[#0d1520] p-3 rounded-xl border border-slate-800">
                  <span className="text-[10px] text-slate-400 block">Luas Bidang (m²)</span>
                  <span className="text-base font-extrabold text-cyan-400 font-mono">
                    {formatIndonesianNumber(selectedMeasurement.areaSquareMeters, 1)} m²
                  </span>
                </div>
                <div className="bg-[#0d1520] p-3 rounded-xl border border-slate-800">
                  <span className="text-[10px] text-slate-400 block">Luas (Hektare)</span>
                  <span className="text-base font-extrabold text-emerald-400 font-mono">
                    {formatIndonesianNumber(selectedMeasurement.areaHectares, 3)} ha
                  </span>
                </div>
                <div className="bg-[#0d1520] p-3 rounded-xl border border-slate-800">
                  <span className="text-[10px] text-slate-400 block">Luas (Are)</span>
                  <span className="text-base font-extrabold text-amber-400 font-mono">
                    {formatIndonesianNumber(selectedMeasurement.areaAre, 2)} are
                  </span>
                </div>
                <div className="bg-[#0d1520] p-3 rounded-xl border border-slate-800">
                  <span className="text-[10px] text-slate-400 block">Keliling Batas</span>
                  <span className="text-base font-extrabold text-slate-200 font-mono">
                    {formatDistanceIndonesian(selectedMeasurement.perimeterMeters)}
                  </span>
                </div>
              </div>

              {/* Map Preview of the Saved Measurement */}
              <div className="relative w-full h-[320px] rounded-2xl overflow-hidden border border-slate-800 my-4 bg-[#0d1520]">
                <div ref={mapContainerRef} className="w-full h-full z-0" />
                <button
                  onClick={() => setShowLayerModal(true)}
                  className="absolute top-3 right-3 z-10 p-2.5 rounded-xl bg-[#111927]/90 backdrop-blur border border-slate-700 text-slate-200 text-xs flex items-center gap-1.5"
                >
                  <Layers className="w-3.5 h-3.5 text-cyan-400" />
                  <span>Layer</span>
                </button>
              </div>

              {/* Coordinates Table */}
              <div className="mt-4">
                <h4 className="text-xs font-bold text-slate-300 mb-2">
                  Daftar Koordinat Patok ({selectedMeasurement.points.length} Titik)
                </h4>
                <div className="overflow-x-auto border border-slate-800 rounded-xl">
                  <table className="w-full text-left text-[11px] text-slate-300 font-mono">
                    <thead>
                      <tr className="bg-[#0d1520] text-slate-400 border-b border-slate-800">
                        <th className="py-2 px-3">No</th>
                        <th className="py-2 px-3">Label</th>
                        <th className="py-2 px-3">Latitude</th>
                        <th className="py-2 px-3">Longitude</th>
                        <th className="py-2 px-3">Akurasi GPS</th>
                        <th className="py-2 px-3">Waktu</th>
                      </tr>
                    </thead>
                    <tbody>
                      {selectedMeasurement.points.map((p, idx) => (
                        <tr key={p.id} className="border-b border-slate-800/50 hover:bg-slate-800/30">
                          <td className="py-2 px-3">{idx + 1}</td>
                          <td className="py-2 px-3 font-bold text-cyan-400">{p.label}</td>
                          <td className="py-2 px-3">{p.latitude.toFixed(6)}°</td>
                          <td className="py-2 px-3">{p.longitude.toFixed(6)}°</td>
                          <td className="py-2 px-3">
                            {p.accuracyMeters ? `±${Math.round(p.accuracyMeters)}m` : '-'}
                          </td>
                          <td className="py-2 px-3">
                            {new Date(p.timestampMillis).toLocaleString('id-ID')}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Export Buttons */}
              <div className="mt-5 pt-4 border-t border-slate-800 flex flex-wrap items-center gap-2">
                <span className="text-xs font-bold text-slate-400 mr-2 flex items-center gap-1">
                  <Download className="w-3.5 h-3.5 text-cyan-400" />
                  Export File:
                </span>

                <button
                  onClick={() => {
                    const geojson = landMeasurementRepository.exportToGeoJson(selectedMeasurement);
                    handleDownloadFile(
                      geojson,
                      `${selectedMeasurement.name.replace(/\s+/g, '_')}.geojson`,
                      'application/geo+json'
                    );
                  }}
                  className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-cyan-300 border border-slate-700 transition"
                >
                  GeoJSON
                </button>

                <button
                  onClick={() => {
                    const kml = landMeasurementRepository.exportToKml(selectedMeasurement);
                    handleDownloadFile(
                      kml,
                      `${selectedMeasurement.name.replace(/\s+/g, '_')}.kml`,
                      'application/vnd.google-earth.kml+xml'
                    );
                  }}
                  className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-emerald-300 border border-slate-700 transition"
                >
                  KML (Google Earth)
                </button>

                <button
                  onClick={() => {
                    const csv = landMeasurementRepository.exportToCsv(selectedMeasurement);
                    handleDownloadFile(
                      csv,
                      `${selectedMeasurement.name.replace(/\s+/g, '_')}.csv`,
                      'text/csv'
                    );
                  }}
                  className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-amber-300 border border-slate-700 transition"
                >
                  CSV Koordinat
                </button>
              </div>
            </div>
          </div>
        )}

        {/* MODE: LIST (Riwayat Pengukuran Tersimpan) */}
        {mode === 'LIST' && (
          <div className="space-y-3">
            {savedMeasurements.length === 0 ? (
              <div className="bg-[#111927] border border-slate-800 rounded-2xl p-8 text-center shadow-lg">
                <div className="w-12 h-12 rounded-2xl bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 flex items-center justify-center mx-auto mb-3">
                  <Ruler className="w-6 h-6" />
                </div>
                <h3 className="text-sm font-bold text-white mb-1">Belum Ada Pengukuran Tersimpan</h3>
                <p className="text-xs text-slate-400 max-w-md mx-auto mb-4">
                  Mulai pengukuran tanah dengan menentukan titik batas lahan menggunakan sensor GPS
                  perangkat atau penandaan langsung pada peta.
                </p>
                <button
                  onClick={() => {
                    setMeasurementName(`Pengukuran ${new Date().toLocaleDateString('id-ID')}`);
                    setActivePoints([]);
                    setMode('SETUP');
                  }}
                  className="px-4 py-2 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs inline-flex items-center gap-2 shadow-lg transition"
                >
                  <Plus className="w-4 h-4" />
                  <span>Mulai Pengukuran Baru</span>
                </button>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
                {savedMeasurements.map((m) => (
                  <div
                    key={m.id}
                    className="bg-[#111927] border border-slate-800 hover:border-slate-700 rounded-2xl p-4 shadow-lg flex flex-col justify-between transition"
                  >
                    <div>
                      <div className="flex items-start justify-between gap-2 mb-2">
                        <h4 className="text-sm font-bold text-white leading-tight">{m.name}</h4>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                          {m.points.length} Titik
                        </span>
                      </div>

                      <div className="text-xs text-slate-400 mb-3 flex items-center gap-2">
                        <Calendar className="w-3 h-3 text-cyan-400" />
                        <span>{m.date}</span>
                        <span>&bull;</span>
                        <span>{m.method === 'POLYGON_FIELD' ? 'Polygon' : 'Garis'}</span>
                      </div>

                      <div className="grid grid-cols-2 gap-2 bg-[#0d1520] p-2.5 rounded-xl border border-slate-800/80 mb-3 text-xs">
                        <div>
                          <span className="text-[10px] text-slate-500 block">Luas</span>
                          <span className="font-bold text-emerald-400 font-mono">
                            {formatIndonesianNumber(m.areaHectares, 3)} ha
                          </span>
                          <span className="text-[10px] text-slate-400 block font-mono">
                            ({formatIndonesianNumber(m.areaSquareMeters, 0)} m²)
                          </span>
                        </div>
                        <div>
                          <span className="text-[10px] text-slate-500 block">Keliling</span>
                          <span className="font-bold text-slate-200 font-mono">
                            {formatDistanceIndonesian(m.perimeterMeters)}
                          </span>
                          {m.gpsAccuracyMetersAvg && (
                            <span className="text-[10px] text-cyan-400 block font-mono">
                              ±{m.gpsAccuracyMetersAvg}m
                            </span>
                          )}
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center justify-between pt-2 border-t border-slate-800/80">
                      <button
                        onClick={() => {
                          setSelectedMeasurement(m);
                          setMode('DETAIL');
                        }}
                        className="px-3 py-1.5 rounded-xl bg-cyan-500/15 border border-cyan-500/30 text-cyan-300 text-xs font-bold hover:bg-cyan-500/25 transition"
                      >
                        Buka Detail & Peta
                      </button>

                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={() => {
                            const text = landMeasurementRepository.formatWhatsAppReport(m);
                            handleCopyText(text);
                          }}
                          className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 transition"
                          title="Salin Laporan WhatsApp"
                        >
                          <Share2 className="w-3.5 h-3.5 text-emerald-400" />
                        </button>
                        <button
                          onClick={() => handleDeleteMeasurement(m.id)}
                          className="p-1.5 rounded-lg bg-slate-800 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 transition"
                          title="Hapus"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* MANDATORY LEGAL & STATUTORY ACCURACY DISCLAIMER */}
        <div className="mt-6 bg-[#0f172a] border border-slate-800/90 rounded-2xl p-4 text-[11px] text-slate-400 leading-relaxed shadow-sm">
          <div className="flex items-start gap-2.5">
            <Info className="w-4 h-4 text-amber-400 flex-shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold text-slate-300 mb-1">
                Pemberitahuan Akurasi & Legalitas Pengukuran:
              </p>
              <p className="mb-1">
                Hasil pengukuran menggunakan GPS perangkat. Akurasi dipengaruhi sinyal GPS, kondisi
                lingkungan, perangkat, dan metode pengukuran.
              </p>
              <p className="text-slate-400">
                Untuk penetapan batas tanah secara resmi, gunakan pengukuran oleh tenaga yang berwenang.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
