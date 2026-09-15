import { FireDataRecord } from '../types';

export const CREATOR_NAME = 'Hardi Mantangai (Suhardi)';
export const CREATOR_PHONE = '085219991118';
export const CREATOR_PHONE_INTL = '6285219991118';
export const CREATOR_PHONE_DISPLAY = '0852-1999-1118';

// Hardi Mantangai geographic center & default BBOX
export const MANTHANGAI_CENTER = {
  latitude: -2.75,
  longitude: 114.25,
  name: 'Kecamatan Mantangai, Kab. Kapuas, Kalimantan Tengah',
};

export const DEFAULT_MANTHANGAI_BBOX = '113.5,-3.5,115.0,-2.0';

/**
 * Calculates distance in kilometers between two coordinates using Haversine formula
 */
export function calculateDistanceKm(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const R = 6371; // Earth radius in km
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

export function formatDistance(distanceKm: number | null | undefined): string {
  if (distanceKm == null || isNaN(distanceKm)) return '-';
  if (distanceKm < 1) {
    return `${Math.round(distanceKm * 1000)} m`;
  }
  return `${distanceKm.toFixed(1)} km`;
}

export function getGoogleMapsDirUrl(lat: number, lon: number): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${lat.toFixed(6)},${lon.toFixed(6)}`;
}

export function getGoogleMapsPinUrl(lat: number, lon: number): string {
  return `https://www.google.com/maps/search/?api=1&query=${lat.toFixed(6)},${lon.toFixed(6)}`;
}

export function generateHotspotShareText(
  record: FireDataRecord,
  distanceKm: number | null
): string {
  const gmapsUrl = getGoogleMapsDirUrl(record.latitude, record.longitude);
  const distanceStr = distanceKm != null ? `\n📍 Jarak dari saya: ${distanceKm.toFixed(1)} km` : '';
  const frpStr = record.frp != null ? `\n🔥 FRP: ${record.frp.toFixed(1)} MW` : '';
  const confStr = record.confidence ? `\n🎯 Keyakinan: ${record.confidence}` : '';

  return (
    `🚨 *INFORMASI TITIK PANAS (HOTSPOT)* 🚨\n` +
    `*HARDI MANTANGAI FIRE NOW*\n\n` +
    `🌐 Koordinat: ${record.latitude.toFixed(5)}°, ${record.longitude.toFixed(5)}°` +
    distanceStr +
    `\n🛰️ Satelit: ${record.satellite} (${record.instrument})` +
    `\n🕒 Waktu Akuisisi: ${record.acqDate} ${record.acqTime || '--'} UTC` +
    frpStr +
    confStr +
    `\n\n🗺️ Petunjuk Arah (Navigasi):\n${gmapsUrl}\n\n` +
    `_Data berbasis sensor satelit NASA FIRMS. Perlu verifikasi darat._\n` +
    `_Aplikasi oleh: ${CREATOR_NAME} (${CREATOR_PHONE_DISPLAY})_`
  );
}

export function openWhatsAppShare(text: string): void {
  const url = `https://api.whatsapp.com/send?text=${encodeURIComponent(text)}`;
  window.open(url, '_blank');
}

export function openWhatsAppContactCreator(): void {
  const defaultText = `Halo Pak Suhardi (Hardi Mantangai), saya menggunakan aplikasi HARDI MANTANGAI FIRE NOW.`;
  const url = `https://wa.me/${CREATOR_PHONE_INTL}?text=${encodeURIComponent(defaultText)}`;
  window.open(url, '_blank');
}
