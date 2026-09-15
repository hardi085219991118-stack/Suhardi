import {
  FireDataRecord,
  FireDataSourceState,
  LiveVerificationGate,
} from '../types';
import { parseFirmsCsv } from '../utils/parser';
import { DEFAULT_MANTHANGAI_BBOX } from '../utils/geo';
import { logger } from './logger';

export const SENSOR_PRIORITY = [
  'VIIRS_NOAA21_NRT',
  'VIIRS_NOAA20_NRT',
  'VIIRS_SNPP_NRT',
  'MODIS_NRT',
];

export const COOLDOWN_SECONDS = 30;
export const RATE_LIMIT_BACKOFF_SECONDS = 60;
const CACHE_STORAGE_KEY = 'hardi_mantangai_fire_cache_v1';
const MAP_KEY_STORAGE_KEY = 'nasa_firms_user_map_key';

export interface FirmsFetchResponse {
  state: FireDataSourceState;
  liveGate: LiveVerificationGate;
  records: FireDataRecord[];
  rawCount: number;
  validCount: number;
  invalidCount: number;
  activeSensor: string;
  satelliteDisplay: string;
  lastUpdateDisplay: string;
  lastFetchDisplay: string;
  dataAgeDisplay: string;
  responseSha256Hash: string | null;
  httpStatusCode: number | null;
  errorMessage?: string;
  isCached?: boolean;
}

class FirmsApiService {
  private lastRequestTimeMillis: number = 0;
  private rateLimitBackoffUntilMillis: number = 0;

  getStoredMapKey(): string {
    return localStorage.getItem(MAP_KEY_STORAGE_KEY) || '';
  }

  setStoredMapKey(key: string): void {
    if (key && key.trim()) {
      localStorage.setItem(MAP_KEY_STORAGE_KEY, key.trim());
      logger.recordEvent('NASA FIRMS MAP_KEY berhasil disimpan pada penyimpanan lokal browser.', 'SUCCESS');
    } else {
      localStorage.removeItem(MAP_KEY_STORAGE_KEY);
      logger.recordEvent('NASA FIRMS MAP_KEY dihapus dari penyimpanan lokal.', 'WARN');
    }
  }

  getCooldownRemainingSeconds(): number {
    const now = Date.now();
    if (now < this.rateLimitBackoffUntilMillis) {
      return Math.ceil((this.rateLimitBackoffUntilMillis - now) / 1000);
    }
    const elapsed = now - this.lastRequestTimeMillis;
    if (elapsed < COOLDOWN_SECONDS * 1000) {
      return Math.ceil((COOLDOWN_SECONDS * 1000 - elapsed) / 1000);
    }
    return 0;
  }

  async fetchFireData(
    bbox: string = DEFAULT_MANTHANGAI_BBOX,
    dayRange: number = 1,
    forceSensor?: string
  ): Promise<FirmsFetchResponse> {
    const now = Date.now();

    // Check rate limit backoff
    if (now < this.rateLimitBackoffUntilMillis) {
      const remainingSec = Math.ceil((this.rateLimitBackoffUntilMillis - now) / 1000);
      logger.recordEvent(`RATE_LIMIT_BACKOFF_ACTIVE: Tunggu ${remainingSec} detik sebelum mencoba lagi.`, 'WARN');
      return {
        state: FireDataSourceState.RATE_LIMIT_EXCEEDED,
        liveGate: LiveVerificationGate.UNVERIFIED,
        records: [],
        rawCount: 0,
        validCount: 0,
        invalidCount: 0,
        activeSensor: '-',
        satelliteDisplay: 'RATE LIMIT NASA',
        lastUpdateDisplay: 'Tertahan Cooldown',
        lastFetchDisplay: new Date().toLocaleTimeString('id-ID'),
        dataAgeDisplay: 'Rate limited',
        responseSha256Hash: null,
        httpStatusCode: 429,
        errorMessage: `Batas frekuensi permintaan NASA FIRMS aktif. Sisa waktu tunggu: ${remainingSec} detik.`,
      };
    }

    // Check cooldown
    const cooldownRemaining = this.getCooldownRemainingSeconds();
    if (cooldownRemaining > 0) {
      logger.recordEvent(`COOLDOWN_ACTIVE: Permintaan ditahan (sisa ${cooldownRemaining} detik).`, 'INFO');
      const cached = this.loadCache();
      if (cached) return cached;
    }

    this.lastRequestTimeMillis = now;
    const userMapKey = this.getStoredMapKey();

    const sensorsToTry = forceSensor ? [forceSensor] : SENSOR_PRIORITY;
    let lastErrorMsg = '';
    let lastStatusCode = 0;

    for (const sensor of sensorsToTry) {
      logger.recordEvent(`LIVE_REQUEST_STARTED: Menghubungi sensor ${sensor} untuk area ${bbox}`, 'INFO');

      try {
        let csvContent: string | null = null;
        let responseHash: string | null = null;
        let statusCode = 200;

        // Step 1: Try proxy endpoint first
        try {
          const queryParams = new URLSearchParams({
            source: sensor,
            bbox,
            dayRange: dayRange.toString(),
          });
          if (userMapKey) {
            queryParams.set('mapKey', userMapKey);
          }

          const headers: Record<string, string> = {
            'Content-Type': 'application/json',
          };
          if (userMapKey) {
            headers['x-map-key'] = userMapKey;
          }

          const response = await fetch(`/api/firms/area?${queryParams.toString()}`, {
            headers,
          });

          if (response.ok) {
            const data = await response.json();
            csvContent = data.csvContent;
            responseHash = data.responseSha256Hash || null;
            statusCode = response.status;
          } else if (response.status === 401 || response.status === 403) {
            const data = await response.json();
            return {
              state: FireDataSourceState.API_CREDENTIAL_REQUIRED,
              liveGate: LiveVerificationGate.UNVERIFIED,
              records: [],
              rawCount: 0,
              validCount: 0,
              invalidCount: 0,
              activeSensor: sensor,
              satelliteDisplay: 'Perlu Kredensial',
              lastUpdateDisplay: 'Belum tersedia',
              lastFetchDisplay: new Date().toLocaleTimeString('id-ID'),
              dataAgeDisplay: 'Belum tersedia',
              responseSha256Hash: data.responseSha256Hash || null,
              httpStatusCode: response.status,
              errorMessage: data.error?.message || 'MAP_KEY NASA FIRMS diperlukan untuk mengakses data satelit langsung.',
            };
          } else if (response.status === 429) {
            this.rateLimitBackoffUntilMillis = Date.now() + RATE_LIMIT_BACKOFF_SECONDS * 1000;
            return {
              state: FireDataSourceState.RATE_LIMIT_EXCEEDED,
              liveGate: LiveVerificationGate.UNVERIFIED,
              records: [],
              rawCount: 0,
              validCount: 0,
              invalidCount: 0,
              activeSensor: sensor,
              satelliteDisplay: 'Rate Limited',
              lastUpdateDisplay: 'Tertahan',
              lastFetchDisplay: new Date().toLocaleTimeString('id-ID'),
              dataAgeDisplay: 'Rate limited',
              responseSha256Hash: null,
              httpStatusCode: 429,
              errorMessage: 'Terlalu banyak permintaan ke server NASA FIRMS. Mohon tunggu 60 detik.',
            };
          }
        } catch {
          // Proxy might not be active, fallback to direct fetch
        }

        // Step 2: Fallback to direct NASA FIRMS API if proxy did not return csv
        if (!csvContent && userMapKey) {
          try {
            const directUrl = `https://firms.modaps.eosdis.nasa.gov/api/area/csv/${encodeURIComponent(userMapKey)}/${sensor}/${bbox}/${dayRange}`;
            const directRes = await fetch(directUrl);
            statusCode = directRes.status;
            if (directRes.ok) {
              const text = await directRes.text();
              if (!text.toLowerCase().includes('invalid map_key')) {
                csvContent = text;
                // Compute SHA256 in browser
                try {
                  const msgBuffer = new TextEncoder().encode(text);
                  const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
                  const hashArray = Array.from(new Uint8Array(hashBuffer));
                  responseHash = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
                } catch {
                  responseHash = null;
                }
              }
            }
          } catch {
            // direct fetch error
          }
        }

        if (csvContent) {
          // Parse CSV
          const instrument = sensor.includes('VIIRS') ? 'VIIRS' : 'MODIS';
          const satellite = sensor.includes('NOAA21')
            ? 'NOAA-21'
            : sensor.includes('NOAA20')
            ? 'NOAA-20'
            : sensor.includes('SNPP')
            ? 'Suomi-NPP'
            : 'Terra/Aqua';

          const parsed = parseFirmsCsv(csvContent, instrument, satellite);

          logger.recordEvent(
            `PARSE_COMPLETED: Sensor ${sensor} berhasil diverifikasi. Valid=${parsed.validCount}, Raw=${parsed.rawCount}, Invalid=${parsed.invalidCount}`,
            'SUCCESS'
          );

          const state =
            parsed.validCount === 0
              ? FireDataSourceState.NO_DETECTIONS_IN_QUERY
              : FireDataSourceState.DATA_SOURCE_AVAILABLE;

          // Format timestamps & age
          let lastUpdateDisplay = 'Belum tersedia';
          let dataAgeDisplay = 'Belum tersedia';
          if (parsed.records.length > 0) {
            const latestRecord = parsed.records.reduce((prev, curr) => {
              const prevTs = prev.acquisitionTimestampMillis || 0;
              const currTs = curr.acquisitionTimestampMillis || 0;
              return currTs > prevTs ? curr : prev;
            }, parsed.records[0]);

            lastUpdateDisplay = `${latestRecord.acqDate} ${latestRecord.acqTime || '--'} UTC`;
            if (latestRecord.acquisitionTimestampMillis) {
              const ageHours = (Date.now() - latestRecord.acquisitionTimestampMillis) / (1000 * 60 * 60);
              dataAgeDisplay = ageHours < 1 ? `${Math.round(ageHours * 60)} menit yang lalu` : `${ageHours.toFixed(1)} jam yang lalu`;
            }
          } else {
            lastUpdateDisplay = 'Tidak ada anomali terdeteksi';
            dataAgeDisplay = 'Terbaru (0 anomali)';
          }

          const result: FirmsFetchResponse = {
            state,
            liveGate: LiveVerificationGate.LIVE_API_VERIFIED,
            records: parsed.records,
            rawCount: parsed.rawCount,
            validCount: parsed.validCount,
            invalidCount: parsed.invalidCount,
            activeSensor: sensor,
            satelliteDisplay: satellite,
            lastUpdateDisplay,
            lastFetchDisplay: new Date().toLocaleTimeString('id-ID'),
            dataAgeDisplay,
            responseSha256Hash: responseHash,
            httpStatusCode: statusCode,
          };

          // Cache valid result
          this.saveCache(result);
          return result;
        }

        lastErrorMsg = `HTTP ${statusCode}`;
      } catch (err: any) {
        lastErrorMsg = err.message || 'Koneksi gagal';
        logger.recordEvent(`SENSOR_REQUEST_FAILED: ${sensor} gagal (${lastErrorMsg})`, 'WARN');
      }
    }

    // If all sensors in priority failed, check offline cache
    const fallback = this.loadCache();
    if (fallback) {
      logger.recordEvent('FALLBACK_TO_CACHE: Menggunakan cache lokal tersimpan.', 'WARN');
      return {
        ...fallback,
        state: FireDataSourceState.CACHED,
        isCached: true,
      };
    }

    logger.recordEvent(`DATA_SOURCE_UNAVAILABLE: Seluruh sensor prioritas gagal dihubungi.`, 'ERROR');
    return {
      state: FireDataSourceState.DATA_SOURCE_UNAVAILABLE,
      liveGate: LiveVerificationGate.VERIFICATION_FAILED,
      records: [],
      rawCount: 0,
      validCount: 0,
      invalidCount: 0,
      activeSensor: '-',
      satelliteDisplay: 'Gagal Memuat',
      lastUpdateDisplay: 'Belum tersedia',
      lastFetchDisplay: new Date().toLocaleTimeString('id-ID'),
      dataAgeDisplay: 'Tidak dapat diakses',
      responseSha256Hash: null,
      httpStatusCode: lastStatusCode || 500,
      errorMessage: lastErrorMsg || 'Seluruh sensor prioritas NASA FIRMS gagal dihubungi.',
    };
  }

  private saveCache(data: FirmsFetchResponse) {
    try {
      localStorage.setItem(
        CACHE_STORAGE_KEY,
        JSON.stringify({
          data,
          cachedAt: Date.now(),
        })
      );
    } catch (e) {
      console.error('Failed to save FIRMS cache', e);
    }
  }

  loadCache(): FirmsFetchResponse | null {
    try {
      const raw = localStorage.getItem(CACHE_STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      // Cache valid up to 24 hours
      if (Date.now() - parsed.cachedAt < 24 * 60 * 60 * 1000) {
        return parsed.data;
      }
    } catch {
      return null;
    }
    return null;
  }
}

export const firmsApi = new FirmsApiService();
