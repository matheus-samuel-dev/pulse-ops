export type UserRole = 'ADMIN' | 'DEVELOPER' | 'VIEWER';
export type Environment = 'PRODUCTION' | 'STAGING' | 'DEVELOPMENT';
export type SystemStatus = 'OPERATIONAL' | 'DEGRADED' | 'DOWN' | 'UNKNOWN' | 'MAINTENANCE' | 'CONFIGURATION_REQUIRED';
export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type IncidentStatus = 'OPEN' | 'INVESTIGATING' | 'RESOLVED';
export type DeploymentStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'ROLLED_BACK';
export type QualityClassification = 'EXCELLENT' | 'GOOD' | 'WARNING' | 'CRITICAL' | 'NO_DATA';
export type NotificationType = 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR' | 'INCIDENT' | 'DEPLOYMENT' | 'QUALITY';

export interface User {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  createdAt?: string;
  updatedAt?: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresAt: string;
  user: User;
}

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface DashboardSummary {
  monitoredSystems: number;
  averageAvailability: number | null;
  availabilityChange: number | null;
  openIncidents: number;
  incidentChange: number;
  averageCoverage: number | null;
  coverageChange: number | null;
  deployments: number;
  operationalSystems: number;
  degradedSystems: number;
  downSystems: number;
  configurationRequiredSystems: number;
  problemSystems: number;
  overallHealth: 'HEALTHY' | 'DEGRADED' | 'CRITICAL' | 'UNKNOWN';
  period: string;
}

export interface LatencyPoint {
  timestamp: string;
  averageMs: number | null;
  p95Ms: number | null;
  samples: number;
}

export interface ErrorBreakdown {
  serverErrors: number;
  clientErrors: number;
  timeouts: number;
  others: number;
  total: number;
  period: string;
}

export interface SystemHealth {
  id: string;
  name: string;
  environment: Environment;
  status: SystemStatus;
  uptime: number | null;
  latencyMs: number | null;
  lastCheckedAt: string | null;
  sparkline: number[];
  active: boolean;
  totalChecks: number;
  statusReason?: string;
  statusChangedAt?: string;
}

export interface DashboardData {
  summary: DashboardSummary;
  latency: LatencyPoint[];
  errors: ErrorBreakdown;
  health: SystemHealth[];
}

export interface ApiErrorPayload {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  validationErrors?: Record<string, string>;
}

export interface MonitoredSystem {
  id: string;
  name: string;
  description?: string;
  baseUrl: string;
  healthEndpoint: string;
  environment: Environment;
  status: SystemStatus;
  active: boolean;
  expectedStatusCode: number;
  timeoutMs: number;
  latencyThresholdMs: number;
  targetAvailability: number;
  statusReason?: string;
  statusChangedAt?: string;
  lastFailureAt?: string;
  monitoringIntervalSeconds: number;
  maintenance: boolean;
  lastCheck?: HealthCheck | null;
  createdAt: string;
  updatedAt: string;
}

export type MonitoredSystemInput = Omit<MonitoredSystem, 'id' | 'status' | 'createdAt' | 'updatedAt' | 'statusReason' | 'statusChangedAt' | 'lastFailureAt' | 'lastCheck'>;

export interface TimeRange { start: string; end: string }

export interface AvailabilityMetrics {
  period: TimeRange;
  totalChecks: number;
  successfulChecks: number;
  failedChecks: number;
  availabilityPercentage: number | null;
  eligibleChecks: number;
  excludedChecks: number;
}

export interface LatencyMetrics {
  period: TimeRange;
  sampleCount: number;
  averageMs: number | null;
  minimumMs?: number;
  maximumMs?: number;
  p95Ms?: number;
}

export interface SlaMetrics {
  period: TimeRange;
  currentAvailability: number | null;
  targetAvailability: number;
  targetMet: boolean;
  differencePercentagePoints: number | null;
  status: 'MET' | 'AT_RISK' | 'BREACHED' | 'NO_DATA';
  evaluatedChecks: number;
}

export interface SystemMetrics {
  period: string;
  availability: AvailabilityMetrics;
  latency: LatencyMetrics;
  sla: SlaMetrics;
}

export interface HealthCheck {
  id: string;
  systemId: string;
  checkedAt: string;
  httpStatus?: number;
  responseTimeMs: number;
  success: boolean;
  errorMessage?: string;
  failureType: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  empty: boolean;
}

export interface Incident {
  id: string;
  systemId: string;
  systemName: string;
  title: string;
  description?: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  startedAt: string;
  investigatingAt?: string;
  resolvedAt?: string;
  automatic: boolean;
  createdAt: string;
}

export interface IncidentInput {
  systemId: string;
  title: string;
  description?: string;
  severity: IncidentSeverity;
  startedAt?: string;
}

export interface Deployment {
  id: string;
  systemId: string;
  systemName: string;
  version: string;
  environment: Environment;
  status: DeploymentStatus;
  deployedAt: string;
  durationSeconds?: number;
  source: string;
  executionUrl?: string;
  commitHash?: string;
  description?: string;
}

export interface DeploymentInput {
  systemId: string;
  version: string;
  environment: Environment;
  commitHash?: string;
  description?: string;
  deployedAt?: string;
}

export interface QualitySummary {
  reportId: string;
  totalTests: number;
  passedTests: number;
  failedTests: number;
  skippedTests: number;
  passRate: number;
  lineCoverage: number;
  branchCoverage: number;
  coverageScore: number;
  classification: QualityClassification;
}

export interface QualityReport extends QualitySummary {
  systemId: string;
  systemName: string;
  environment: Environment;
  generatedAt: string;
  source: string;
}

export interface QualityOverview {
  monitoredSystems: number;
  systemsWithReports: number;
  systemsWithoutReports: number;
  totalTests: number;
  passedTests: number;
  failedTests: number;
  skippedTests: number;
  passRate: number;
  averageLineCoverage: number | null;
  averageBranchCoverage: number | null;
  averageCoverageScore: number | null;
  classification: QualityClassification;
  systems: QualityReport[];
}

export interface Notification {
  id: string;
  title: string;
  message: string;
  type: NotificationType;
  read: boolean;
  createdAt: string;
  eventId?: string;
  systemId?: string;
  resourceId?: string;
}

export interface NotificationPage {
  items: Notification[];
  unreadCount: number;
}

export interface UserInput {
  name: string;
  email: string;
  password: string;
  role: UserRole;
}

export interface OperationalReportKpis {
  monitoredSystems: number;
  operationalSystems: number;
  degradedSystems: number;
  downSystems: number;
  unknownSystems: number;
  totalHealthChecks: number;
  successfulHealthChecks: number;
  failedHealthChecks: number;
  availability: number | null;
  activeIncidents: number;
  incidentsOpened: number;
  deployments: number;
  successfulDeployments: number;
  deploymentSuccessRate: number | null;
  totalTests: number;
  passedTests: number;
  failedTests: number;
  skippedTests: number;
  testPassRate: number | null;
  averageLineCoverage: number | null;
  averageBranchCoverage: number | null;
}

export interface OperationalEvent {
  id: string;
  type: 'HEALTH_CHECK' | 'INCIDENT' | 'DEPLOYMENT' | 'QUALITY' | 'SYSTEM' | 'ACCOUNT' | 'INTEGRATION';
  occurredAt: string;
  systemId: string;
  systemName: string;
  environment: Environment;
  title: string;
  description?: string;
  status: string;
  impact: 'INFO' | 'SUCCESS' | 'WARNING' | 'CRITICAL';
  source: string;
}

export interface OperationalReport {
  period: string;
  environment?: Environment;
  windowStart: string;
  windowEnd: string;
  kpis: OperationalReportKpis;
  feed: OperationalEvent[];
  totalEvents: number;
  eventPages: number;
  eventPage: number;
}
