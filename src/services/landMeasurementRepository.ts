import { LandMeasurement } from '../types/landMeasurement';
import { formatAreaUnits, formatDistanceIndonesian, formatIndonesianNumber } from './landMeasurementEngine';

const STORAGE_KEY = 'hardi_mantangai_land_measurements_v1';

export const landMeasurementRepository = {
  /**
   * Retrieve all saved measurements
   */
  getAll(): LandMeasurement[] {
    try {
      const data = localStorage.getItem(STORAGE_KEY);
      if (!data) return [];
      const parsed = JSON.parse(data);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  },

  /**
   * Save a measurement
   */
  save(measurement: LandMeasurement): void {
    const list = this.getAll();
    const index = list.findIndex((m) => m.id === measurement.id);
    if (index >= 0) {
      list[index] = measurement;
    } else {
      list.unshift(measurement);
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
  },

  /**
   * Get single measurement by id
   */
  getById(id: string): LandMeasurement | null {
    const list = this.getAll();
    return list.find((m) => m.id === id) || null;
  },

  /**
   * Delete a measurement
   */
  delete(id: string): void {
    const list = this.getAll();
    const filtered = list.filter((m) => m.id !== id);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(filtered));
  },

  /**
   * Export to GeoJSON standard string
   */
  exportToGeoJson(measurement: LandMeasurement): string {
    const isPolygon = measurement.method === 'POLYGON_FIELD';
    const coords = measurement.points.map((p) => [p.longitude, p.latitude, p.altitudeMeters || 0]);

    if (isPolygon && coords.length >= 3) {
      coords.push([...coords[0]]); // close ring
    }

    const geoJson = {
      type: 'FeatureCollection',
      metadata: {
        app: 'Hardi Mantangai Fire Now - Pengukuran Tanah',
        measurementId: measurement.id,
        name: measurement.name,
        date: measurement.date,
        method: measurement.method,
        perimeterMeters: measurement.perimeterMeters,
        areaSquareMeters: measurement.areaSquareMeters,
        areaHectares: measurement.areaHectares,
        gpsAccuracyMetersAvg: measurement.gpsAccuracyMetersAvg,
        exportedAt: new Date().toISOString(),
      },
      features: [
        {
          type: 'Feature',
          properties: {
            name: measurement.name,
            method: measurement.method,
            areaSquareMeters: measurement.areaSquareMeters,
            areaHectares: measurement.areaHectares,
            perimeterMeters: measurement.perimeterMeters,
          },
          geometry: isPolygon
            ? {
                type: 'Polygon',
                coordinates: [coords],
              }
            : {
                type: 'LineString',
                coordinates: coords,
              },
        },
        ...measurement.points.map((p) => ({
          type: 'Feature',
          properties: {
            pointNumber: p.pointNumber,
            label: p.label,
            altitudeMeters: p.altitudeMeters,
            accuracyMeters: p.accuracyMeters,
            timestamp: new Date(p.timestampMillis).toISOString(),
          },
          geometry: {
            type: 'Point',
            coordinates: [p.longitude, p.latitude, p.altitudeMeters || 0],
          },
        })),
      ],
    };

    return JSON.stringify(geoJson, null, 2);
  },

  /**
   * Export to KML (Keyhole Markup Language) string for Google Earth / GIS
   */
  exportToKml(measurement: LandMeasurement): string {
    const isPolygon = measurement.method === 'POLYGON_FIELD';
    const coordsStr = measurement.points
      .map((p) => `${p.longitude},${p.latitude},${p.altitudeMeters || 0}`)
      .join(' ');

    const closedCoordsStr = isPolygon && measurement.points.length >= 3
      ? `${coordsStr} ${measurement.points[0].longitude},${measurement.points[0].latitude},${measurement.points[0].altitudeMeters || 0}`
      : coordsStr;

    return `<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>${escapeXml(measurement.name)}</name>
    <description>Pengukuran Tanah - Hardi Mantangai Fire Now (${measurement.date})</description>
    <Style id="landPolyStyle">
      <LineStyle>
        <color>ff00ffff</color>
        <width>3</width>
      </LineStyle>
      <PolyStyle>
        <color>4000ffff</color>
      </PolyStyle>
    </Style>
    <Placemark>
      <name>${escapeXml(measurement.name)}</name>
      <styleUrl>#landPolyStyle</styleUrl>
      ${
        isPolygon
          ? `<Polygon>
        <extrude>1</extrude>
        <altitudeMode>clampToGround</altitudeMode>
        <outerBoundaryIs>
          <LinearRing>
            <coordinates>${closedCoordsStr}</coordinates>
          </LinearRing>
        </outerBoundaryIs>
      </Polygon>`
          : `<LineString>
        <extrude>1</extrude>
        <altitudeMode>clampToGround</altitudeMode>
        <coordinates>${closedCoordsStr}</coordinates>
      </LineString>`
      }
    </Placemark>
    ${measurement.points
      .map(
        (p) => `<Placemark>
      <name>${p.label}</name>
      <Point>
        <coordinates>${p.longitude},${p.latitude},${p.altitudeMeters || 0}</coordinates>
      </Point>
    </Placemark>`
      )
      .join('\n    ')}
  </Document>
</kml>`;
  },

  /**
   * Export coordinate points to CSV string
   */
  exportToCsv(measurement: LandMeasurement): string {
    const headers = ['No', 'Label', 'Latitude', 'Longitude', 'Altitude (m)', 'Akurasi GPS (m)', 'Waktu'];
    const rows = measurement.points.map((p) => [
      p.pointNumber,
      `"${p.label}"`,
      p.latitude.toFixed(7),
      p.longitude.toFixed(7),
      p.altitudeMeters !== null && p.altitudeMeters !== undefined ? p.altitudeMeters.toFixed(1) : '-',
      p.accuracyMeters !== null && p.accuracyMeters !== undefined ? `±${p.accuracyMeters.toFixed(1)}` : '-',
      new Date(p.timestampMillis).toLocaleString('id-ID'),
    ]);

    const metadataRows = [
      `# Nama Pengukuran: ${measurement.name}`,
      `# Tanggal: ${measurement.date}`,
      `# Metode: ${measurement.method}`,
      `# Luas: ${formatIndonesianNumber(measurement.areaSquareMeters, 1)} m2 (${formatIndonesianNumber(measurement.areaHectares, 3)} ha)`,
      `# Keliling: ${formatIndonesianNumber(measurement.perimeterMeters, 1)} m`,
      `# Akurasi GPS Rata-rata: ${measurement.gpsAccuracyMetersAvg ? `±${measurement.gpsAccuracyMetersAvg} m` : '-'}`,
      '',
    ];

    return [...metadataRows, headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
  },

  /**
   * Format WhatsApp alert/summary text
   */
  formatWhatsAppReport(measurement: LandMeasurement): string {
    const area = formatAreaUnits(measurement.areaSquareMeters);
    const perimeter = formatDistanceIndonesian(measurement.perimeterMeters);

    return `*LAPORAN PENGUKURAN TANAH*
*Hardi Mantangai Fire Now*
----------------------------------------
*Nama:* ${measurement.name}
*Tanggal:* ${measurement.date}
*Metode:* ${measurement.method === 'POLYGON_FIELD' ? 'Polygon / Bidang' : 'Garis / Titik'}
*Jumlah Titik:* ${measurement.points.length} Titik (P1 s/d P${measurement.points.length})

*HASIL PERHITUNGAN GEODESIK:*
• *Luas Bidang:* ${area.squareMeters} (${area.hectares})
• *Luas (Are):* ${area.are}
• *Keliling Batas:* ${perimeter}
• *Akurasi GPS Rata-rata:* ${measurement.gpsAccuracyMetersAvg ? `±${measurement.gpsAccuracyMetersAvg} m` : 'Belum tersedia'}

*TITIK KOORDINAT BATAS:*
${measurement.points
  .map((p) => `• ${p.label}: ${p.latitude.toFixed(6)}, ${p.longitude.toFixed(6)} (±${p.accuracyMeters ? Math.round(p.accuracyMeters) : '-'}m)`)
  .join('\n')}

_Catatan: Hasil pengukuran menggunakan sensor GPS perangkat. Untuk penetapan batas resmi, gunakan pengukuran oleh instansi berwenang._`;
  },
};

function escapeXml(unsafe: string): string {
  return unsafe.replace(/[<>&'"]/g, (c) => {
    switch (c) {
      case '<':
        return '&lt;';
      case '>':
        return '&gt;';
      case '&':
        return '&amp;';
      case '\'':
        return '&apos;';
      case '"':
        return '&quot;';
      default:
        return c;
    }
  });
}
