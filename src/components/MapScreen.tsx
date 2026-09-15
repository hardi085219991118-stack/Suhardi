import React, { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import {
  Layers,
  Compass,
  Flame,
  ZoomIn,
  ZoomOut,
  Crosshair,
  RefreshCw,
  Info,
  Check,
} from 'lucide-react';
import {
  FireDataRecord,
  LocationData,
  BaseMapLayer,
  MapStatus,
} from '../types';
import { MANTHANGAI_CENTER, calculateDistanceKm, formatDistance } from '../utils/geo';

interface MapScreenProps {
  records: FireDataRecord[];
  userLocation: LocationData | null;
  onSelectRecord: (record: FireDataRecord) => void;
  onRefresh: () => void;
  isRefreshing: boolean;
  cooldownSeconds: number;
}

export const MapScreen: React.FC<MapScreenProps> = ({
  records,
  userLocation,
  onSelectRecord,
  onRefresh,
  isRefreshing,
  cooldownSeconds,
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const layerGroupRef = useRef<L.LayerGroup | null>(null);
  const tileLayerRef = useRef<L.TileLayer | null>(null);

  const [activeLayer, setActiveLayer] = useState<BaseMapLayer>(BaseMapLayer.OPEN_STREET_MAP);
  const [showLayerModal, setShowLayerModal] = useState(false);
  const [mapStatus, setMapStatus] = useState<MapStatus>(MapStatus.MAP_LOADING);

  // Initialize Map
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    try {
      const initialLat = userLocation?.latitude || MANTHANGAI_CENTER.latitude;
      const initialLon = userLocation?.longitude || MANTHANGAI_CENTER.longitude;

      const map = L.map(mapContainerRef.current, {
        center: [initialLat, initialLon],
        zoom: 9,
        zoomControl: false,
        attributionControl: false,
      });

      // Initial Tile Layer
      const osmTile = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
      });
      osmTile.addTo(map);
      tileLayerRef.current = osmTile;

      const layerGroup = L.layerGroup().addTo(map);
      layerGroupRef.current = layerGroup;

      mapInstanceRef.current = map;
      setMapStatus(MapStatus.MAP_READY);
    } catch (e) {
      console.error('Failed to initialize map', e);
      setMapStatus(MapStatus.MAP_ERROR);
    }

    return () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    };
  }, []);

  // Update Tile Layer when activeLayer changes
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (tileLayerRef.current) {
      map.removeLayer(tileLayerRef.current);
    }

    let url = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
    let maxZoom = 19;

    if (activeLayer === BaseMapLayer.SATELLITE_HYBRID) {
      url = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}';
      maxZoom = 18;
    }

    const newTile = L.tileLayer(url, { maxZoom }).addTo(map);
    tileLayerRef.current = newTile;
  }, [activeLayer]);

  // Render Hotspots and Location Markers
  useEffect(() => {
    const map = mapInstanceRef.current;
    const group = layerGroupRef.current;
    if (!map || !group) return;

    group.clearLayers();

    // 1. Hardi Mantangai Center marker
    const mantangaiIcon = L.divIcon({
      className: 'custom-mantangai-pin',
      html: `
        <div style="display:flex;flex-direction:column;align-items:center;">
          <div style="background:#1e88e5;color:white;padding:3px 7px;border-radius:6px;font-size:10px;font-weight:bold;white-space:nowrap;box-shadow:0 2px 6px rgba(0,0,0,0.5);border:1px solid rgba(255,255,255,0.4);">
            Mantangai (Pusat)
          </div>
          <div style="width:10px;height:10px;background:#1e88e5;border-radius:50%;border:2px solid white;box-shadow:0 2px 4px rgba(0,0,0,0.4);margin-top:2px;"></div>
        </div>
      `,
      iconSize: [100, 36],
      iconAnchor: [50, 36],
    });
    L.marker([MANTHANGAI_CENTER.latitude, MANTHANGAI_CENTER.longitude], { icon: mantangaiIcon })
      .bindPopup(
        `<div style="font-size:12px;padding:4px;"><strong>Pusat Wilayah Mantangai</strong><br>Kab. Kapuas, Kalteng</div>`
      )
      .addTo(group);

    // 2. User GPS Location Marker
    if (userLocation) {
      const userGpsIcon = L.divIcon({
        className: 'user-gps-marker',
        html: `
          <div style="position:relative;width:24px;height:24px;display:flex;align-items:center;justify-content:center;">
            <div style="position:absolute;width:24px;height:24px;border-radius:50%;background:rgba(0,230,118,0.3);animation:ping 2s cubic-bezier(0,0,0.2,1) infinite;"></div>
            <div style="width:14px;height:14px;border-radius:50%;background:#00e676;border:2.5px solid white;box-shadow:0 0 8px #00e676;"></div>
          </div>
        `,
        iconSize: [24, 24],
        iconAnchor: [12, 12],
      });

      L.marker([userLocation.latitude, userLocation.longitude], { icon: userGpsIcon })
        .bindPopup(
          `<div style="font-size:12px;padding:4px;"><strong>Lokasi Anda (GPS)</strong><br>Akurasi: ±${userLocation.accuracyMeters ? Math.round(userLocation.accuracyMeters) : 10} m</div>`
        )
        .addTo(group);

      if (userLocation.accuracyMeters && userLocation.accuracyMeters > 0) {
        L.circle([userLocation.latitude, userLocation.longitude], {
          radius: userLocation.accuracyMeters,
          color: '#00e676',
          weight: 1,
          fillColor: '#00e676',
          fillOpacity: 0.1,
        }).addTo(group);
      }
    }

    // 3. Fire Hotspot Markers
    records.forEach((record) => {
      const frp = record.frp || 0;
      const isHighFrp = frp >= 25;
      const isMediumFrp = frp >= 10 && frp < 25;

      const flameColor = isHighFrp ? '#d50000' : isMediumFrp ? '#ff6d00' : '#ff9100';
      const flameAura = isHighFrp ? 'rgba(213,0,0,0.4)' : isMediumFrp ? 'rgba(255,109,0,0.3)' : 'rgba(255,145,0,0.25)';

      const flameIcon = L.divIcon({
        className: 'fire-hotspot-pin',
        html: `
          <div style="cursor:pointer;position:relative;width:32px;height:32px;display:flex;align-items:center;justify-content:center;">
            <div style="position:absolute;width:30px;height:30px;border-radius:50%;background:${flameAura};"></div>
            <div style="width:24px;height:24px;border-radius:50%;background:${flameColor};display:flex;align-items:center;justify-content:center;box-shadow:0 2px 6px rgba(0,0,0,0.6);border:1.5px solid white;">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="white" stroke="none">
                <path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"/>
              </svg>
            </div>
          </div>
        `,
        iconSize: [32, 32],
        iconAnchor: [16, 16],
      });

      const marker = L.marker([record.latitude, record.longitude], { icon: flameIcon });

      const distKm = userLocation
        ? calculateDistanceKm(userLocation.latitude, userLocation.longitude, record.latitude, record.longitude)
        : null;

      const popupHtml = `
        <div style="font-family:sans-serif;padding:6px;min-width:180px;">
          <div style="display:flex;align-items:center;gap:6px;margin-bottom:4px;">
            <span style="background:${flameColor};color:white;font-size:10px;font-weight:bold;padding:2px 6px;border-radius:4px;">HOTSPOT</span>
            <span style="font-size:11px;font-weight:bold;color:#ff5722;">${record.satellite}</span>
          </div>
          <div style="font-size:12px;font-family:monospace;font-weight:bold;margin-bottom:4px;">
            ${record.latitude.toFixed(5)}°, ${record.longitude.toFixed(5)}°
          </div>
          <div style="font-size:11px;color:#94a3b8;margin-bottom:2px;">
            FRP: <strong style="color:#ffb74d;">${record.frp ? record.frp.toFixed(1) + ' MW' : '-'}</strong> | Keyakinan: <strong>${record.confidence || '-'}</strong>
          </div>
          ${distKm !== null ? `<div style="font-size:11px;color:#00e676;font-weight:bold;margin-bottom:6px;">📍 Jarak: ${formatDistance(distKm)}</div>` : ''}
          <div style="margin-top:6px;border-top:1px solid rgba(255,255,255,0.1);padding-top:6px;text-align:center;">
            <button id="btn-detail-${record.latitude}-${record.longitude}" style="background:#ff5722;color:white;border:none;border-radius:6px;padding:4px 10px;font-size:11px;font-weight:bold;cursor:pointer;width:100%;">
              Buka Detail Lengkap
            </button>
          </div>
        </div>
      `;

      marker.bindPopup(popupHtml);

      marker.on('popupopen', () => {
        const btn = document.getElementById(`btn-detail-${record.latitude}-${record.longitude}`);
        if (btn) {
          btn.onclick = () => onSelectRecord(record);
        }
      });

      marker.addTo(group);
    });
  }, [records, userLocation]);

  const handleZoomIn = () => mapInstanceRef.current?.zoomIn();
  const handleZoomOut = () => mapInstanceRef.current?.zoomOut();

  const handleCenterMantangai = () => {
    mapInstanceRef.current?.flyTo([MANTHANGAI_CENTER.latitude, MANTHANGAI_CENTER.longitude], 10, {
      duration: 1.2,
    });
  };

  const handleCenterUser = () => {
    if (userLocation && mapInstanceRef.current) {
      mapInstanceRef.current.flyTo([userLocation.latitude, userLocation.longitude], 12, {
        duration: 1.2,
      });
    }
  };

  return (
    <div className="relative w-full h-[calc(100vh-120px)] md:h-[calc(100vh-64px)] overflow-hidden bg-[#0d1520]">
      {/* The Leaflet Container */}
      <div ref={mapContainerRef} className="w-full h-full z-0" />

      {/* Floating Header Info Bar on Map */}
      <div className="absolute top-3 left-3 right-3 sm:right-auto z-10 flex flex-wrap items-center gap-2">
        <div className="bg-[#111927]/90 backdrop-blur-md border border-slate-700/80 rounded-xl px-3.5 py-2 shadow-lg flex items-center gap-2.5">
          <div className="w-6 h-6 rounded-full bg-orange-500/20 text-orange-400 flex items-center justify-center">
            <Flame className="w-3.5 h-3.5" />
          </div>
          <div>
            <span className="text-xs font-bold text-white block leading-tight">
              {records.length} Titik Panas
            </span>
            <span className="text-[10px] text-slate-400 font-mono">
              Wilayah Hardi Mantangai
            </span>
          </div>
        </div>

        {/* Refresh button with cooldown indicator */}
        <button
          onClick={onRefresh}
          disabled={isRefreshing || cooldownSeconds > 0}
          className="bg-[#111927]/90 backdrop-blur-md border border-slate-700/80 hover:border-slate-600 disabled:opacity-75 rounded-xl px-3 py-2 shadow-lg text-xs font-semibold text-slate-200 flex items-center gap-1.5 transition active:scale-95"
        >
          <RefreshCw
            className={`w-3.5 h-3.5 text-sky-400 ${
              isRefreshing ? 'animate-spin' : ''
            }`}
          />
          <span>
            {isRefreshing
              ? 'Memperbarui...'
              : cooldownSeconds > 0
              ? `${cooldownSeconds}s`
              : 'Perbarui'}
          </span>
        </button>
      </div>

      {/* Layer Selection Modal - STRICTLY 2 OPTIONS (NO USGS) */}
      {showLayerModal && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#111927] border border-slate-700 w-full max-w-sm rounded-2xl p-5 shadow-2xl">
            <h3 className="text-sm font-bold text-slate-100 mb-1 flex items-center gap-2">
              <Layers className="w-4 h-4 text-emerald-400" />
              PILIHAN LAYER PETA
            </h3>
            <p className="text-[11px] text-slate-400 mb-4">
              Pilih tampilan peta latar belakang satelit atau vektor jalan:
            </p>

            <div className="space-y-2.5">
              {/* Pilihan 1: Citra Satelit (Esri World Imagery) */}
              <button
                onClick={() => {
                  setActiveLayer(BaseMapLayer.SATELLITE_HYBRID);
                  setShowLayerModal(false);
                }}
                className={`w-full p-3 rounded-xl border text-left flex items-center justify-between transition ${
                  activeLayer === BaseMapLayer.SATELLITE_HYBRID
                    ? 'border-emerald-500 bg-emerald-500/10 text-emerald-300'
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
                  <Check className="w-4 h-4 text-emerald-400" />
                )}
              </button>

              {/* Pilihan 2: Peta Standar (OpenStreetMap) */}
              <button
                onClick={() => {
                  setActiveLayer(BaseMapLayer.OPEN_STREET_MAP);
                  setShowLayerModal(false);
                }}
                className={`w-full p-3 rounded-xl border text-left flex items-center justify-between transition ${
                  activeLayer === BaseMapLayer.OPEN_STREET_MAP
                    ? 'border-emerald-500 bg-emerald-500/10 text-emerald-300'
                    : 'border-slate-700 bg-slate-800/60 text-slate-200 hover:border-slate-600'
                }`}
              >
                <div>
                  <div className="text-xs font-bold">2. Peta Standar (OpenStreetMap)</div>
                  <div className="text-[10px] text-slate-400 mt-0.5">
                    Peta jalan, toponimi & kontur wilayah
                  </div>
                </div>
                {activeLayer === BaseMapLayer.OPEN_STREET_MAP && (
                  <Check className="w-4 h-4 text-emerald-400" />
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

      {/* Top-Right Secondary Controls: Layer Switcher & Compass */}
      <div className="absolute top-3 right-3 z-10 flex items-center gap-2">
        {/* Layer Switcher */}
        <button
          onClick={() => setShowLayerModal(true)}
          className="h-10 px-3 rounded-xl bg-[#111927]/90 backdrop-blur-md border border-slate-700/80 hover:border-slate-600 text-slate-200 shadow-xl flex items-center gap-1.5 transition active:scale-95"
          title="Pilih Layer Peta"
        >
          <Layers
            className={`w-4 h-4 ${
              activeLayer === BaseMapLayer.SATELLITE_HYBRID
                ? 'text-emerald-400'
                : 'text-sky-400'
            }`}
          />
          <span className="text-xs font-semibold hidden sm:inline">
            {activeLayer === BaseMapLayer.SATELLITE_HYBRID ? 'Esri Satelit' : 'OSM Standar'}
          </span>
        </button>

        {/* Center to Hardi Mantangai */}
        <button
          onClick={handleCenterMantangai}
          className="w-10 h-10 rounded-xl bg-[#111927]/90 backdrop-blur-md border border-slate-700/80 hover:border-slate-600 text-slate-200 shadow-xl flex items-center justify-center transition active:scale-95"
          title="Pusatkan ke Mantangai"
        >
          <Compass className="w-5 h-5 text-orange-400" />
        </button>
      </div>

      {/* Right-Side Map Navigation Controls: Zoom Container followed by GPS with generous safe margin */}
      <div className="absolute right-3 top-16 sm:top-16 z-10 flex flex-col items-center">
        {/* Zoom In & Out Container */}
        <div className="flex flex-col rounded-xl overflow-hidden border border-slate-700/80 bg-[#111927]/95 backdrop-blur-md shadow-xl">
          <button
            onClick={handleZoomIn}
            className="w-10 h-10 flex items-center justify-center text-slate-300 hover:text-white hover:bg-slate-800 transition active:scale-95"
            title="Perbesar Peta (+)"
            aria-label="Perbesar Peta"
          >
            <ZoomIn className="w-4 h-4 text-slate-200" />
          </button>
          <div className="h-px bg-slate-800" />
          <button
            onClick={handleZoomOut}
            className="w-10 h-10 flex items-center justify-center text-slate-300 hover:text-white hover:bg-slate-800 transition active:scale-95"
            title="Perkecil Peta (−)"
            aria-label="Perkecil Peta"
          >
            <ZoomOut className="w-4 h-4 text-slate-200" />
          </button>
        </div>

        {/* Dedicated Margin Separation for GPS Control (Never touching or overlapping) */}
        {userLocation && (
          <div className="mt-4">
            <button
              onClick={handleCenterUser}
              className="w-10 h-10 rounded-xl bg-[#111927]/95 backdrop-blur-md border border-slate-700/80 hover:border-slate-600 text-slate-200 shadow-xl flex items-center justify-center transition active:scale-95"
              title="Pusatkan ke Lokasi Saya (GPS)"
              aria-label="Pusatkan ke Lokasi Saya"
            >
              <Crosshair className="w-5 h-5 text-emerald-400" />
            </button>
          </div>
        )}
      </div>

      {/* Map Legend Banner at Bottom Left */}
      <div className="absolute bottom-4 left-3 z-10 hidden sm:flex items-center gap-3 bg-[#111927]/85 backdrop-blur-md border border-slate-800 px-3 py-1.5 rounded-xl text-[11px] text-slate-300 shadow-lg">
        <div className="flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-full bg-[#d50000] inline-block"></span>
          <span>FRP Tinggi (≥25MW)</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-full bg-[#ff6d00] inline-block"></span>
          <span>FRP Sedang</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-full bg-[#ff9100] inline-block"></span>
          <span>FRP Rendah</span>
        </div>
        {userLocation && (
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-[#00e676] inline-block"></span>
            <span>GPS Anda</span>
          </div>
        )}
      </div>
    </div>
  );
};
