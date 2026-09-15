import { FieldPoint, LandMeasurement, MeasurementMethod } from '../types/landMeasurement';

const EARTH_RADIUS_METERS = 6378137; // WGS84 mean equatorial radius

/**
 * Calculates the geodesic distance in meters between two lat/lon coordinates
 * using the Haversine formula.
 */
export function calculateGeodesicDistanceMeters(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const radLat1 = (lat1 * Math.PI) / 180;
  const radLat2 = (lat2 * Math.PI) / 180;

  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);

  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return EARTH_RADIUS_METERS * c;
}

/**
 * Calculates the total perimeter or path length for a series of field points.
 * If isClosedPolygon is true and points >= 3, includes the closing segment P_last -> P_1.
 */
export function calculatePerimeterMeters(
  points: FieldPoint[],
  isClosedPolygon: boolean = true
): number {
  if (points.length < 2) return 0;

  let totalMeters = 0;
  for (let i = 0; i < points.length - 1; i++) {
    totalMeters += calculateGeodesicDistanceMeters(
      points[i].latitude,
      points[i].longitude,
      points[i + 1].latitude,
      points[i + 1].longitude
    );
  }

  if (isClosedPolygon && points.length >= 3) {
    totalMeters += calculateGeodesicDistanceMeters(
      points[points.length - 1].latitude,
      points[points.length - 1].longitude,
      points[0].latitude,
      points[0].longitude
    );
  }

  return totalMeters;
}

/**
 * Calculates the geodesic area in square meters for a closed spherical polygon.
 * Requires at least 3 points. Uses the spherical excess / trapezoidal formulation
 * based on WGS84 radius.
 */
export function calculateGeodesicAreaSquareMeters(points: FieldPoint[]): number {
  if (points.length < 3) return 0;

  let total = 0;
  const numPoints = points.length;

  for (let i = 0; i < numPoints; i++) {
    const p1 = points[i];
    const p2 = points[(i + 1) % numPoints];

    const lon1Rad = (p1.longitude * Math.PI) / 180;
    const lon2Rad = (p2.longitude * Math.PI) / 180;
    const lat1Rad = (p1.latitude * Math.PI) / 180;
    const lat2Rad = (p2.latitude * Math.PI) / 180;

    // Spherical excess trapezoidal component
    total += (lon2Rad - lon1Rad) * Math.sin((lat1Rad + lat2Rad) / 2);
  }

  const area = Math.abs(total * EARTH_RADIUS_METERS * EARTH_RADIUS_METERS);
  return area;
}

/**
 * Compute average GPS accuracy across all points
 */
export function calculateAverageAccuracyMeters(points: FieldPoint[]): number | null {
  const pointsWithAccuracy = points.filter(
    (p) => p.accuracyMeters !== undefined && p.accuracyMeters !== null && p.accuracyMeters > 0
  );
  if (pointsWithAccuracy.length === 0) return null;

  const sum = pointsWithAccuracy.reduce((acc, p) => acc + (p.accuracyMeters || 0), 0);
  return Math.round((sum / pointsWithAccuracy.length) * 10) / 10;
}

/**
 * Format numbers using Indonesian punctuation conventions (dots for thousands, commas for decimals)
 */
export function formatIndonesianNumber(val: number, decimals: number = 2): string {
  if (isNaN(val) || !isFinite(val)) return '0';
  return val.toLocaleString('id-ID', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

/**
 * Format distance in meters or kilometers with Indonesian format
 */
export function formatDistanceIndonesian(meters: number): string {
  if (meters >= 1000) {
    const km = meters / 1000;
    return `${formatIndonesianNumber(km, 2)} km`;
  }
  return `${formatIndonesianNumber(meters, 1)} m`;
}

/**
 * Format area in various units
 */
export interface FormattedAreaUnits {
  squareMeters: string;
  are: string;
  hectares: string;
  squareKm: string;
}

export function formatAreaUnits(sqMeters: number): FormattedAreaUnits {
  const are = sqMeters / 100;
  const hectares = sqMeters / 10000;
  const sqKm = sqMeters / 1000000;

  return {
    squareMeters: `${formatIndonesianNumber(sqMeters, 1)} m²`,
    are: `${formatIndonesianNumber(are, 2)} are`,
    hectares: `${formatIndonesianNumber(hectares, 3)} ha`,
    squareKm: `${formatIndonesianNumber(sqKm, 4)} km²`,
  };
}

/**
 * Build a complete LandMeasurement object from points and metadata
 */
export function createLandMeasurement(
  name: string,
  method: MeasurementMethod,
  points: FieldPoint[],
  notes?: string
): LandMeasurement {
  const isPolygon = method === 'POLYGON_FIELD';
  const perimeter = calculatePerimeterMeters(points, isPolygon);
  const distance = calculatePerimeterMeters(points, false);
  const areaSqM = isPolygon ? calculateGeodesicAreaSquareMeters(points) : 0;
  const now = Date.now();

  return {
    id: `MEAS_${now}_${Math.random().toString(36).substring(2, 7)}`,
    name: name.trim() || `Pengukuran Tanah ${new Date(now).toLocaleDateString('id-ID')}`,
    date: new Date(now).toLocaleDateString('id-ID', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }),
    timestampMillis: now,
    method,
    points,
    distanceMeters: distance,
    perimeterMeters: perimeter,
    areaSquareMeters: areaSqM,
    areaHectares: areaSqM / 10000,
    areaAre: areaSqM / 100,
    areaSquareKm: areaSqM / 1000000,
    gpsAccuracyMetersAvg: calculateAverageAccuracyMeters(points),
    notes,
    createdAt: now,
  };
}
