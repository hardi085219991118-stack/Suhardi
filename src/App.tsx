import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  FireDataRecord,
  LocationData,
  LocationStatus,
  FireDataSourceState,
  LiveVerificationGate,
  AppBottomNavTab,
  HotspotFilterCriteria,
} from './types';
import { firmsApi, FirmsFetchResponse } from './services/firmsApi';
import { logger } from './services/logger';
import { calculateDistanceKm, DEFAULT_MANTHANGAI_BBOX } from './utils/geo';
import { Navigation } from './components/Navigation';
import { DashboardScreen } from './components/DashboardScreen';
import { MapScreen } from './components/MapScreen';
import { HotspotsListScreen } from './components/HotspotsListScreen';
import { InfoSystemScreen } from './components/InfoSystemScreen';
import { LandMeasurementScreen } from './components/landMeasurement/LandMeasurementScreen';
import { HotspotDetailModal } from './components/HotspotDetailModal';
import { FilterHotspotsModal } from './components/FilterHotspotsModal';
import { MapKeyModal } from './components/MapKeyModal';

export const App: React.FC = () => {
  // Navigation
  const [currentTab, setCurrentTab] = useState<AppBottomNavTab>(AppBottomNavTab.BERANDA);

  // Satellite Fire Data State
  const [records, setRecords] = useState<FireDataRecord[]>([]);
  const [fireDataState, setFireDataState] = useState<FireDataSourceState>(FireDataSourceState.CONNECTING);
  const [liveGate, setLiveGate] = useState<LiveVerificationGate>(LiveVerificationGate.UNVERIFIED);
  const [activeSensor, setActiveSensor] = useState<string>('VIIRS_NOAA21_NRT');
  const [satelliteDisplay, setSatelliteDisplay] = useState<string>('Menghubungi...');
  const [lastUpdateDisplay, setLastUpdateDisplay] = useState<string>('Belum tersedia');
  const [lastFetchDisplay, setLastFetchDisplay] = useState<string>('Belum tersedia');
  const [dataAgeDisplay, setDataAgeDisplay] = useState<string>('Memverifikasi...');
  const [responseSha256Hash, setResponseSha256Hash] = useState<string | null>(null);

  // User GPS Location State
  const [userLocation, setUserLocation] = useState<LocationData | null>(null);
  const [locationStatus, setLocationStatus] = useState<LocationStatus>(LocationStatus.LOCATION_UNAVAILABLE);

  // Modals & Selections
  const [selectedRecord, setSelectedRecord] = useState<FireDataRecord | null>(null);
  const [isFilterModalOpen, setIsFilterModalOpen] = useState(false);
  const [isMapKeyModalOpen, setIsMapKeyModalOpen] = useState(false);

  // Filter Criteria
  const [filterCriteria, setFilterCriteria] = useState<HotspotFilterCriteria>({
    maxDistanceKm: null,
    satellite: null,
    maxAgeHours: null,
  });

  // Cooldown & Loading states
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [cooldownSeconds, setCooldownSeconds] = useState(0);

  // 1. Cooldown ticker
  useEffect(() => {
    const timer = setInterval(() => {
      const rem = firmsApi.getCooldownRemainingSeconds();
      setCooldownSeconds(rem);
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  // 2. Real GPS Location Handler
  const requestDeviceLocation = useCallback(() => {
    if (!('geolocation' in navigator)) {
      setLocationStatus(LocationStatus.LOCATION_UNAVAILABLE);
      logger.recordEvent('GEOLOCATION_UNAVAILABLE: Peramban tidak mendukung sensor lokasi.', 'WARN');
      return;
    }

    setLocationStatus(LocationStatus.LOCATION_LOADING);
    logger.recordEvent('REQUESTING_GPS: Membaca sinyal sensor posisi perangkat...', 'INFO');

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const loc: LocationData = {
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
          accuracyMeters: pos.coords.accuracy,
          timeMillis: pos.timestamp,
          isMock: false,
          runtimeEnvironment: 'BROWSER_WEB',
          verificationLevel: 'REAL_DEVICE_VERIFIED',
        };
        setUserLocation(loc);
        setLocationStatus(LocationStatus.LOCATION_AVAILABLE);
        logger.recordEvent(
          `GPS_LOCKED: Posisi berhasil diperoleh (${loc.latitude.toFixed(4)}°, ${loc.longitude.toFixed(4)}°, akurasi ±${Math.round(pos.coords.accuracy)}m)`,
          'SUCCESS'
        );
      },
      (err) => {
        if (err.code === err.PERMISSION_DENIED) {
          setLocationStatus(LocationStatus.LOCATION_PERMISSION_DENIED);
          logger.recordEvent('GPS_PERMISSION_DENIED: Izin akses lokasi ditolak oleh pengguna.', 'WARN');
        } else {
          setLocationStatus(LocationStatus.LOCATION_ERROR);
          logger.recordEvent(`GPS_ERROR: Gagal membaca posisi (${err.message})`, 'WARN');
        }
      },
      {
        enableHighAccuracy: true,
        timeout: 15000,
        maximumAge: 30000,
      }
    );
  }, []);

  // 3. Fetch Fire Data from NASA FIRMS
  const fetchFireData = useCallback(async () => {
    setIsRefreshing(true);
    try {
      const response: FirmsFetchResponse = await firmsApi.fetchFireData(
        DEFAULT_MANTHANGAI_BBOX,
        1
      );

      setRecords(response.records);
      setFireDataState(response.state);
      setLiveGate(response.liveGate);
      setActiveSensor(response.activeSensor);
      setSatelliteDisplay(response.satelliteDisplay);
      setLastUpdateDisplay(response.lastUpdateDisplay);
      setLastFetchDisplay(response.lastFetchDisplay);
      setDataAgeDisplay(response.dataAgeDisplay);
      setResponseSha256Hash(response.responseSha256Hash);
    } catch (e: any) {
      logger.recordEvent(`FETCH_FAILED: ${e.message}`, 'ERROR');
    } finally {
      setIsRefreshing(false);
    }
  }, []);

  // Initial mount load
  useEffect(() => {
    requestDeviceLocation();
    fetchFireData();
  }, [requestDeviceLocation, fetchFireData]);

  // Filtered Records calculation
  const filteredRecords = useMemo(() => {
    return records.filter((rec) => {
      // 1. Distance filter
      if (filterCriteria.maxDistanceKm !== null && filterCriteria.maxDistanceKm !== undefined && userLocation) {
        const dist = calculateDistanceKm(
          userLocation.latitude,
          userLocation.longitude,
          rec.latitude,
          rec.longitude
        );
        if (dist > filterCriteria.maxDistanceKm) return false;
      }

      // 2. Satellite filter
      if (filterCriteria.satellite) {
        if (!rec.satellite.toLowerCase().includes(filterCriteria.satellite.toLowerCase())) {
          return false;
        }
      }

      // 3. Max age filter
      if (filterCriteria.maxAgeHours !== null && filterCriteria.maxAgeHours !== undefined && rec.acquisitionTimestampMillis) {
        const ageHours = (Date.now() - rec.acquisitionTimestampMillis) / (1000 * 60 * 60);
        if (ageHours > filterCriteria.maxAgeHours) return false;
      }

      return true;
    });
  }, [records, filterCriteria, userLocation]);

  return (
    <div className="min-h-screen bg-[#090e17] text-slate-100 flex flex-col font-sans selection:bg-orange-500/30 selection:text-orange-200">
      {/* Top Header & Navigation */}
      <Navigation
        currentTab={currentTab}
        onTabSelected={setCurrentTab}
        hotspotCount={records.length}
      />

      {/* Main Content Body */}
      <main className="flex-1 w-full overflow-x-hidden">
        {currentTab === AppBottomNavTab.BERANDA && (
          <DashboardScreen
            records={records}
            userLocation={userLocation}
            locationStatus={locationStatus}
            fireDataState={fireDataState}
            liveGate={liveGate}
            activeSensor={activeSensor}
            satelliteDisplay={satelliteDisplay}
            lastUpdateDisplay={lastUpdateDisplay}
            dataAgeDisplay={dataAgeDisplay}
            responseSha256Hash={responseSha256Hash}
            cooldownSeconds={cooldownSeconds}
            isRefreshing={isRefreshing}
            onRequestLocation={requestDeviceLocation}
            onRefreshSatellite={fetchFireData}
            onNavigateToMap={() => setCurrentTab(AppBottomNavTab.PETA)}
            onNavigateToList={() => setCurrentTab(AppBottomNavTab.TITIK_PANAS)}
            onOpenMapKeyModal={() => setIsMapKeyModalOpen(true)}
          />
        )}

        {currentTab === AppBottomNavTab.PETA && (
          <MapScreen
            records={filteredRecords}
            userLocation={userLocation}
            onSelectRecord={setSelectedRecord}
            onRefresh={fetchFireData}
            isRefreshing={isRefreshing}
            cooldownSeconds={cooldownSeconds}
          />
        )}

        {currentTab === AppBottomNavTab.TITIK_PANAS && (
          <HotspotsListScreen
            records={filteredRecords}
            allRecordsCount={records.length}
            userLocation={userLocation}
            filterCriteria={filterCriteria}
            onOpenFilter={() => setIsFilterModalOpen(true)}
            onResetFilter={() =>
              setFilterCriteria({
                maxDistanceKm: null,
                satellite: null,
                maxAgeHours: null,
              })
            }
            onSelectRecord={setSelectedRecord}
          />
        )}

        {currentTab === AppBottomNavTab.PENGUKURAN_TANAH && (
          <LandMeasurementScreen
            userLocation={userLocation}
            onRequestLocation={requestDeviceLocation}
          />
        )}

        {currentTab === AppBottomNavTab.INFO && (
          <InfoSystemScreen
            records={records}
            userLocation={userLocation}
            locationStatus={locationStatus}
            fireDataState={fireDataState}
            activeSensor={activeSensor}
            satelliteDisplay={satelliteDisplay}
            lastUpdateDisplay={lastUpdateDisplay}
            lastFetchDisplay={lastFetchDisplay}
            responseSha256Hash={responseSha256Hash}
            onOpenMapKeyModal={() => setIsMapKeyModalOpen(true)}
            onRefreshSatellite={fetchFireData}
            isRefreshing={isRefreshing}
          />
        )}
      </main>

      {/* Modals */}
      {selectedRecord && (
        <HotspotDetailModal
          record={selectedRecord}
          userLocation={userLocation}
          onClose={() => setSelectedRecord(null)}
          onViewOnMap={(rec) => {
            setCurrentTab(AppBottomNavTab.PETA);
          }}
        />
      )}

      {isFilterModalOpen && (
        <FilterHotspotsModal
          criteria={filterCriteria}
          onApply={setFilterCriteria}
          onReset={() =>
            setFilterCriteria({
              maxDistanceKm: null,
              satellite: null,
              maxAgeHours: null,
            })
          }
          onClose={() => setIsFilterModalOpen(false)}
          hasDeviceLocation={Boolean(userLocation)}
        />
      )}

      {isMapKeyModalOpen && (
        <MapKeyModal
          onClose={() => setIsMapKeyModalOpen(false)}
          onSaved={() => {
            fetchFireData();
          }}
        />
      )}
    </div>
  );
};
