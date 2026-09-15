import {
  calculateGeodesicDistanceMeters,
  calculatePerimeterMeters,
  calculateGeodesicAreaSquareMeters,
  formatAreaUnits,
  formatDistanceIndonesian,
  createLandMeasurement,
} from '../services/landMeasurementEngine';
import { landMeasurementRepository } from '../services/landMeasurementRepository';
import { FieldPoint } from '../types/landMeasurement';
import { calculateDistanceKm, DEFAULT_MANTHANGAI_BBOX, MANTHANGAI_CENTER } from '../utils/geo';
import { firmsApi } from '../services/firmsApi';

function assert(condition: boolean, message: string) {
  if (!condition) {
    console.error(`❌ FAILED: ${message}`);
    process.exit(1);
  }
  console.log(`✅ PASS: ${message}`);
}

console.log('=== HARDI MANTANGAI FIRE NOW — SUITE PENGUJIAN REGRESI & FITUR BARU ===\n');

// 1. LOCKED FEATURE REGRESSION: Geo utilities & BBOX Mantangai
console.log('--- 1. Pengujian Fitur Terkunci (Locked): Koordinat & Geo Mantangai ---');
assert(DEFAULT_MANTHANGAI_BBOX === '113.5,-3.5,115.0,-2.0', 'BBOX Mantangai tetap terkunci');
assert(MANTHANGAI_CENTER.latitude === -2.75, 'Center Mantangai latitude terkunci');
assert(MANTHANGAI_CENTER.longitude === 114.25, 'Center Mantangai longitude terkunci');

const testDistKm = calculateDistanceKm(-2.75, 114.25, -2.75, 114.26);
assert(testDistKm > 1.0 && testDistKm < 1.2, `Jarak 0.01 derajat lon ~1.11 km (${testDistKm.toFixed(3)} km)`);

// 2. LOCKED FEATURE REGRESSION: FIRMS API integrity & sensors
console.log('\n--- 2. Pengujian Fitur Terkunci (Locked): Sensor Loop & Cooldown Engine ---');
assert(firmsApi.getCooldownRemainingSeconds() === 0, 'Cooldown awal bernilai 0 detik');
assert(typeof firmsApi.fetchFireData === 'function', 'firmsApi.fetchFireData tersedia');

// 3. LAND MEASUREMENT ENGINE: Geodesic distance & perimeter
console.log('\n--- 3. Pengujian Fitur Baru: Land Measurement Engine (Geodetik) ---');
const p1: FieldPoint = {
  id: 'p1',
  pointNumber: 1,
  label: 'P1',
  latitude: -2.850000,
  longitude: 114.550000,
  timestampMillis: Date.now(),
  orderIndex: 0,
};
const p2: FieldPoint = {
  id: 'p2',
  pointNumber: 2,
  label: 'P2',
  latitude: -2.850000,
  longitude: 114.550898, // ~100m east along equator/parallel
  timestampMillis: Date.now(),
  orderIndex: 1,
};
const p3: FieldPoint = {
  id: 'p3',
  pointNumber: 3,
  label: 'P3',
  latitude: -2.850904, // ~100m south
  longitude: 114.550898,
  timestampMillis: Date.now(),
  orderIndex: 2,
};
const p4: FieldPoint = {
  id: 'p4',
  pointNumber: 4,
  label: 'P4',
  latitude: -2.850904,
  longitude: 114.550000,
  timestampMillis: Date.now(),
  orderIndex: 3,
};

const d12 = calculateGeodesicDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
assert(Math.abs(d12 - 99.8) < 5, `Jarak p1-p2 mendekati 100 meter (didapat ${d12.toFixed(1)}m)`);

// Perimeter 4 points rectangle (~400m)
const perimeter = calculatePerimeterMeters([p1, p2, p3, p4], true);
assert(Math.abs(perimeter - 400) < 15, `Keliling 4 titik persegi mendekati 400 meter (didapat ${perimeter.toFixed(1)}m)`);

// Area of ~100m x ~100m square (~10,000 m² = 1 hectare)
const areaSqM = calculateGeodesicAreaSquareMeters([p1, p2, p3, p4]);
assert(Math.abs(areaSqM - 10000) < 500, `Luas 100mx100m mendekati 10.000 m2 / 1 ha (didapat ${areaSqM.toFixed(1)} m2)`);

// Minimum points validation: < 3 points must yield 0 area
const areaTwoPoints = calculateGeodesicAreaSquareMeters([p1, p2]);
assert(areaTwoPoints === 0, '2 titik tidak menghasilkan luas bidang (< 3 titik bernilai 0)');

// 4. FORMATTING & UNIT CONVERSIONS (Indonesian Format)
console.log('\n--- 4. Pengujian Konversi Satuan & Format Bahasa Indonesia ---');
const formatted = formatAreaUnits(12500);
assert(formatted.hectares.includes('ha'), 'Format hektare menyertakan simbol ha');
assert(formatted.are.includes('are'), 'Format are menyertakan simbol are');
assert(formatted.squareMeters.includes('m²'), 'Format m² menyertakan simbol m²');

const distFormatted = formatDistanceIndonesian(1450);
assert(distFormatted.includes('km'), `Jarak 1450m dikonversi ke km: ${distFormatted}`);

// 5. LAND MEASUREMENT OBJECT CREATION & EXPORTS
console.log('\n--- 5. Pengujian Pembuatan Objek & Format Export (GeoJSON, KML, CSV) ---');
const meas = createLandMeasurement(
  'Lahan Uji Coba Mantangai',
  'POLYGON_FIELD',
  [p1, p2, p3, p4],
  'Patok kayu batas parit'
);
assert(meas.points.length === 4, 'Objek pengukuran menyimpan 4 titik patok');
assert(meas.areaHectares > 0.9 && meas.areaHectares < 1.1, `Luas dalam hektare ~1 ha (${meas.areaHectares.toFixed(3)} ha)`);

const geojsonStr = landMeasurementRepository.exportToGeoJson(meas);
const parsedGeoJson = JSON.parse(geojsonStr);
assert(parsedGeoJson.type === 'FeatureCollection', 'GeoJSON bertipe FeatureCollection');
assert(parsedGeoJson.features.length >= 2, 'GeoJSON berisi feature Polygon dan Points');

const kmlStr = landMeasurementRepository.exportToKml(meas);
assert(kmlStr.includes('<kml') && kmlStr.includes('</kml>'), 'KML valid dengan tag pembuka dan penutup');
assert(kmlStr.includes('<Polygon>'), 'KML menyertakan LinearRing/Polygon');

const csvStr = landMeasurementRepository.exportToCsv(meas);
assert(csvStr.includes('Latitude') && csvStr.includes('Longitude'), 'CSV memiliki header koordinat');
assert(csvStr.includes('P1') && csvStr.includes('P4'), 'CSV menyertakan titik P1 s/d P4');

const waReport = landMeasurementRepository.formatWhatsAppReport(meas);
assert(waReport.includes('LAPORAN PENGUKURAN TANAH'), 'Laporan WhatsApp memiliki judul');
assert(waReport.includes('Luas Bidang:'), 'Laporan WhatsApp memuat luas bidang');

console.log('\n============================================================');
console.log('SELURUH PENGUJIAN REGRESI DAN FITUR BARU: 100% PASS');
console.log('============================================================');
