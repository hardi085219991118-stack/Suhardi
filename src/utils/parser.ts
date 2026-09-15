import { FireDataRecord } from '../types';

export interface ParsedCsvResult {
  records: FireDataRecord[];
  rawCount: number;
  validCount: number;
  invalidCount: number;
}

/**
 * Parses NASA FIRMS CSV output with strict validation (Zero Dummy mandate).
 */
export function parseFirmsCsv(
  csvContent: string,
  defaultInstrument: string = 'VIIRS',
  defaultSatellite: string = 'NOAA-21'
): ParsedCsvResult {
  if (!csvContent || !csvContent.trim()) {
    return { records: [], rawCount: 0, validCount: 0, invalidCount: 0 };
  }

  const lines = csvContent
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line.length > 0);

  if (lines.length <= 1) {
    return { records: [], rawCount: 0, validCount: 0, invalidCount: 0 };
  }

  // Header inspection
  const header = lines[0].split(',').map(h => h.trim().toLowerCase());
  const latIndex = header.indexOf('latitude');
  const lonIndex = header.indexOf('longitude');

  if (latIndex === -1 || lonIndex === -1) {
    throw new Error('Header CSV NASA FIRMS tidak memiliki kolom latitude atau longitude yang valid.');
  }

  const brightnessIndex = header.indexOf('brightness') !== -1 ? header.indexOf('brightness') : header.indexOf('bright_ti4');
  const scanIndex = header.indexOf('scan');
  const trackIndex = header.indexOf('track');
  const acqDateIndex = header.indexOf('acq_date');
  const acqTimeIndex = header.indexOf('acq_time');
  const satIndex = header.indexOf('satellite');
  const instIndex = header.indexOf('instrument');
  const confIndex = header.indexOf('confidence');
  const verIndex = header.indexOf('version');
  const brightT31Index = header.indexOf('bright_t31') !== -1 ? header.indexOf('bright_t31') : header.indexOf('bright_ti5');
  const frpIndex = header.indexOf('frp');
  const daynightIndex = header.indexOf('daynight');

  const records: FireDataRecord[] = [];
  let invalidCount = 0;
  const rawRows = lines.slice(1);

  for (const rowStr of rawRows) {
    // Handle comma inside quotes if any, though FIRMS CSV is strictly plain comma-separated
    const cols = rowStr.split(',').map(c => c.trim().replace(/^["']|["']$/g, ''));
    if (cols.length < 2) {
      invalidCount++;
      continue;
    }

    const lat = parseFloat(cols[latIndex]);
    const lon = parseFloat(cols[lonIndex]);

    // Strict coordinate validation
    if (isNaN(lat) || isNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      invalidCount++;
      continue;
    }

    const acqDate = acqDateIndex !== -1 && cols[acqDateIndex] ? cols[acqDateIndex] : '';
    const acqTime = acqTimeIndex !== -1 && cols[acqTimeIndex] ? cols[acqTimeIndex] : '';

    // Calculate UTC epoch timestamp
    let tsMillis: number | null = null;
    if (acqDate) {
      try {
        let cleanTime = acqTime.padStart(4, '0');
        if (cleanTime.includes(':')) {
          cleanTime = cleanTime.replace(':', '');
        }
        const hours = parseInt(cleanTime.slice(0, 2), 10);
        const minutes = parseInt(cleanTime.slice(2, 4), 10);
        if (!isNaN(hours) && !isNaN(minutes)) {
          const isoString = `${acqDate}T${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:00.000Z`;
          const d = new Date(isoString);
          if (!isNaN(d.getTime())) {
            tsMillis = d.getTime();
          }
        }
      } catch {
        tsMillis = null;
      }
    }

    const satRaw = satIndex !== -1 && cols[satIndex] ? cols[satIndex] : defaultSatellite;
    const satFormatted = formatSatelliteName(satRaw);

    const record: FireDataRecord = {
      latitude: lat,
      longitude: lon,
      brightness: brightnessIndex !== -1 && !isNaN(parseFloat(cols[brightnessIndex])) ? parseFloat(cols[brightnessIndex]) : null,
      scan: scanIndex !== -1 && !isNaN(parseFloat(cols[scanIndex])) ? parseFloat(cols[scanIndex]) : null,
      track: trackIndex !== -1 && !isNaN(parseFloat(cols[trackIndex])) ? parseFloat(cols[trackIndex]) : null,
      acqDate,
      acqTime,
      satellite: satFormatted,
      instrument: instIndex !== -1 && cols[instIndex] ? cols[instIndex] : defaultInstrument,
      confidence: confIndex !== -1 && cols[confIndex] ? cols[confIndex] : null,
      version: verIndex !== -1 && cols[verIndex] ? cols[verIndex] : null,
      brightT31: brightT31Index !== -1 && !isNaN(parseFloat(cols[brightT31Index])) ? parseFloat(cols[brightT31Index]) : null,
      frp: frpIndex !== -1 && !isNaN(parseFloat(cols[frpIndex])) ? parseFloat(cols[frpIndex]) : null,
      daynight: daynightIndex !== -1 && cols[daynightIndex] ? cols[daynightIndex] : null,
      acquisitionTimestampMillis: tsMillis,
    };

    records.push(record);
  }

  return {
    records,
    rawCount: rawRows.length,
    validCount: records.length,
    invalidCount,
  };
}

export function formatSatelliteName(raw: string): string {
  if (!raw) return 'NASA Satellite';
  const upper = raw.toUpperCase();
  if (upper === 'N' || upper.includes('NOAA-21') || upper.includes('NOAA21') || upper === '21') return 'NOAA-21';
  if (upper === 'J' || upper.includes('NOAA-20') || upper.includes('NOAA20') || upper === '20') return 'NOAA-20';
  if (upper === 'NPP' || upper.includes('SNPP') || upper.includes('SUOMI')) return 'Suomi-NPP';
  if (upper === 'T' || upper.includes('TERRA')) return 'Terra (MODIS)';
  if (upper === 'A' || upper.includes('AQUA')) return 'Aqua (MODIS)';
  return raw;
}
