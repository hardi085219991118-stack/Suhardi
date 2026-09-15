export interface FireDataRecord {
  latitude: number;
  longitude: number;
  brightness?: number | null;
  scan?: number | null;
  track?: number | null;
  acqDate: string;
  acqTime: string;
  satellite: string;
  instrument: string;
  confidence?: string | null;
  version?: string | null;
  brightT31?: number | null;
  frp?: number | null;
  daynight?: string | null;
  acquisitionTimestampMillis?: number | null;
}

export type LocationVerificationLevel = 
  | 'UNVERIFIED'
  | 'REAL_DEVICE_VERIFIED'
  | 'MOCK'
  | 'PERMISSION_DENIED';

export type RuntimeEnvironment = 
  | 'PRODUCTION'
  | 'BROWSER_WEB'
  | 'SANDBOX'
  | 'EMULATOR';

export interface LocationData {
  latitude: number;
  longitude: number;
  accuracyMeters?: number | null;
  provider?: string;
  timeMillis: number;
  isMock: boolean;
  runtimeEnvironment: RuntimeEnvironment;
  verificationLevel: LocationVerificationLevel;
}

export enum LocationStatus {
  LOCATION_UNAVAILABLE = 'LOCATION_UNAVAILABLE',
  LOCATION_PERMISSION_REQUIRED = 'LOCATION_PERMISSION_REQUIRED',
  LOCATION_PERMISSION_DENIED = 'LOCATION_PERMISSION_DENIED',
  LOCATION_PROVIDER_DISABLED = 'LOCATION_PROVIDER_DISABLED',
  LOCATION_LOADING = 'LOCATION_LOADING',
  LOCATION_AVAILABLE = 'LOCATION_AVAILABLE',
  LOCATION_ERROR = 'LOCATION_ERROR'
}

export enum FireDataSourceState {
  NOT_VERIFIED = 'NOT_VERIFIED',
  CONNECTING = 'CONNECTING',
  DATA_SOURCE_AVAILABLE = 'DATA_SOURCE_AVAILABLE',
  NO_DETECTIONS_IN_QUERY = 'NO_DETECTIONS_IN_QUERY',
  CACHED = 'CACHED',
  API_CREDENTIAL_REQUIRED = 'API_CREDENTIAL_REQUIRED',
  RATE_LIMIT_EXCEEDED = 'RATE_LIMIT_EXCEEDED',
  DATA_SOURCE_UNAVAILABLE = 'DATA_SOURCE_UNAVAILABLE',
  INVALID_DATA_RESPONSE = 'INVALID_DATA_RESPONSE',
  TIMEOUT = 'TIMEOUT',
  NETWORK_ERROR = 'NETWORK_ERROR'
}

export enum LiveVerificationGate {
  UNVERIFIED = 'UNVERIFIED',
  LIVE_API_VERIFIED = 'LIVE_API_VERIFIED',
  VERIFICATION_FAILED = 'VERIFICATION_FAILED'
}

export enum BaseMapLayer {
  OPEN_STREET_MAP = 'OPEN_STREET_MAP',
  SATELLITE_HYBRID = 'SATELLITE_HYBRID'
}

export enum MapStatus {
  MAP_LOADING = 'MAP_LOADING',
  MAP_READY = 'MAP_READY',
  MAP_ERROR = 'MAP_ERROR'
}

export interface HotspotFilterCriteria {
  maxDistanceKm?: number | null;
  satellite?: string | null;
  maxAgeHours?: number | null;
}

export enum HotspotSortOrder {
  TERBARU = 'TERBARU',
  TERDEKAT = 'TERDEKAT',
  FRP_TERTINGGI = 'FRP_TERTINGGI'
}

export enum AppBottomNavTab {
  BERANDA = 'BERANDA',
  PETA = 'PETA',
  TITIK_PANAS = 'TITIK_PANAS',
  PENGUKURAN_TANAH = 'PENGUKURAN_TANAH',
  INFO = 'INFO'
}

export enum InfoSubTab {
  RINGKASAN = 'RINGKASAN',
  AUDIT_TEKNIS = 'AUDIT_TEKNIS',
  TENTANG = 'TENTANG'
}

export interface AuditEvent {
  id: string;
  timestamp: number;
  message: string;
  type: 'INFO' | 'WARN' | 'ERROR' | 'SUCCESS';
}

export interface AppErrorLog {
  id: string;
  timestamp: number;
  type: string;
  message: string;
  source: string;
  recoveryAction?: string;
}
