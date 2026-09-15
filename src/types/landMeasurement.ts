export type MeasurementMethod = 
  | 'POLYGON_FIELD'
  | 'GPS_TRACK'
  | 'POINT_TO_POINT'
  | 'MANUAL_POINTS';

export interface FieldPoint {
  id: string;
  pointNumber: number;
  label: string;
  latitude: number;
  longitude: number;
  altitudeMeters?: number | null;
  accuracyMeters?: number | null;
  timestampMillis: number;
  orderIndex: number;
}

export interface LandMeasurement {
  id: string;
  name: string;
  date: string;
  timestampMillis: number;
  method: MeasurementMethod;
  points: FieldPoint[];
  distanceMeters: number;
  perimeterMeters: number;
  areaSquareMeters: number;
  areaHectares: number;
  areaAre: number;
  areaSquareKm: number;
  gpsAccuracyMetersAvg: number | null;
  notes?: string;
  createdAt: number;
}
