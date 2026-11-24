/**
 * US workstation registration typed clients (us-station-registration-web task 1.1)
 * - the 登记 page's transport against the REAL `/api/v1/us/**` backend (no mock
 * layer, ruling 2026-11-05). SELF-CONTAINED module (types + client in one file) -
 * the `patient.ts` precedent: `./types.ts` currently carries a parallel session's
 * uncommitted work, and coupling two changes' commits through it is worse than a
 * local module (the types MAY migrate for uniformity once that change archives).
 *
 * Contract mirror: `org.birdbro.lumen.us.controller.RegistrationController` +
 * its DTOs (RegistrationCreateRequest / RegistrationOrderView / the resolve
 * passthrough over `PatientLookupResult`), kernel `ExamItemRef` ({itemId,
 * itemName} catalog reference + name snapshot), and `ExamOrderStatus`
 * (BOOKED/REGISTERED/IN_PROGRESS/COMPLETED/BOOKING_CANCELLED/CANCELLED).
 */
import { apiRequest } from './client';

export type UsOrderStatus =
  | 'BOOKED'
  | 'REGISTERED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'BOOKING_CANCELLED'
  | 'CANCELLED';

/** The kernel ExamItemRef shape: catalog id + the name snapshot taken at order time. */
export interface UsExamItemRef {
  itemId: number;
  itemName: string;
  /** The dict-1024 token riding to the issuer for scoped rule resolution
   * (number-rule-modality-scope) - row-derived from the setting catalog. */
  modality?: string | null;
}

/** The jsonb patient snapshot the kernel carries on the order (display projection). */
export interface UsPatientSnapshot {
  patId?: string | null;
  name?: string | null;
  gender?: number | null;
  phone?: string | null;
  age?: string | null;
  patientSource?: string | null;
  [key: string]: unknown;
}

/** The us_registration_ext extension block (decision #5: 孕检归 us 域). */
export interface UsRegistrationExtBody {
  pregnancyStatus?: string | null;
  gestationalWeeks?: number | null;
  note?: string | null;
}

/** The demographics-or-identity create body (the server resolves-or-registers).
 * `bookingTime` (us-registration-redesign-api 拍板 02-B): the pre-registration slot
 * anchor - null/absent = 现场单 (submit registers), a future `yyyy-MM-dd HH:mm:ss`
 * = 预登记 (capacity-validated server-side; the four A0400 refusals surface verbatim). */
export interface UsOrderCreateBody {
  idType?: string | null;
  idNo?: string | null;
  cardNo?: string | null;
  name?: string | null;
  gender?: number | null;
  phone?: string | null;
  age?: string | null;
  patientSource?: string | null;
  admissionNumber?: string | null;
  bedNumber?: string | null;
  height?: number | null;
  weight?: number | null;
  bookingTime?: string | null;
  examItems: UsExamItemRef[];
  emgFlag: number;
  ext?: UsRegistrationExtBody | null;
}

export interface UsOrderView {
  orderId: number;
  accessionNo: string;
  status: UsOrderStatus;
  patientUid: number;
  patientSnapshot: UsPatientSnapshot | null;
  examItems: UsExamItemRef[];
  emgFlag: number;
  cancelReason: string | null;
  createTime: string;
  /** 预登记时段锚点 (null = 现场单/无预约; 拍板 02-B). */
  bookingTime: string | null;
  pregnancyStatus: string | null;
  gestationalWeeks: number | null;
  note: string | null;
}

/** The wide-table query (us-registration-redesign-api 拍板 03-B, the RCP∪EIS union).
 * `status` rides as the comma-joined IN string (the Spring list binder); the apply-time
 * range bounds create_time (the registration moment). */
export interface UsOrderPageQuery {
  status?: UsOrderStatus[];
  accessionNo?: string | null;
  patientName?: string | null;
  patientId?: string | null;
  patientSource?: string | null;
  cardNo?: string | null;
  examItemName?: string | null;
  applyTimeStart?: string | null;
  applyTimeEnd?: string | null;
}

/** The resolve passthrough outcome (patient decision #2 chain, no side effect). */
export interface UsPatientIdentity {
  patUid: number;
  patId: string;
  state?: string | null;
  idType?: string | null;
  idNo?: string | null;
  name?: string | null;
  gender?: number | null;
  birthDate?: string | null;
  phone?: string | null;
  cardNo?: string | null;
}

export interface UsLookupResult {
  result: 'MATCHED' | 'CANDIDATES' | 'NONE';
  identity: UsPatientIdentity | null;
  candidates: UsPatientIdentity[] | null;
}

/** The lookup query: the demographics keys the chain walks (id_no → card_no → soft). */
export interface UsLookupQuery {
  idType?: string | null;
  idNo?: string | null;
  cardNo?: string | null;
  name?: string | null;
  gender?: number | null;
  phone?: string | null;
}

export interface UsOrderPage {
  records: UsOrderView[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export function resolvePatients(query: UsLookupQuery): Promise<UsLookupResult> {
  return apiRequest<UsLookupResult>('/us/patients/resolve', { method: 'POST', body: query });
}

export function createOrder(
  body: UsOrderCreateBody,
): Promise<{ orderId: number; accessionNo: string; status: UsOrderStatus; bookingTime: string | null }> {
  return apiRequest<{ orderId: number; accessionNo: string; status: UsOrderStatus; bookingTime: string | null }>(
    '/us/orders',
    { method: 'POST', body },
  );
}

export function pageOrders(
  query: { pageNum: number; pageSize: number } & UsOrderPageQuery,
): Promise<UsOrderPage> {
  return apiRequest<UsOrderPage>('/us/orders', { query: serializeOrderQuery(query) });
}

/** The status-chip meter (EIS ReportStatusCheckbox semantics): the SAME filter set
 * minus status, zero-filled across all six states server-side. */
export function statusCounts(query: UsOrderPageQuery): Promise<Record<UsOrderStatus, number>> {
  const { pageNum: _p, pageSize: _s, ...filters } = query as typeof query & {
    pageNum?: number;
    pageSize?: number;
  };
  return apiRequest<Record<UsOrderStatus, number>>('/us/orders/status-counts', {
    query: serializeOrderQuery(filters),
  });
}

/** The query serializer: the status array joins into the comma-separated IN string
 * (the client's query record carries no arrays - the Spring list binder reads the
 * joined value), blanks drop out. */
function serializeOrderQuery(
  query: { pageNum?: number; pageSize?: number } & UsOrderPageQuery,
): Record<string, string | number | boolean> {
  const params: Record<string, string | number | boolean> = {};
  if (query.pageNum != null) params.pageNum = query.pageNum;
  if (query.pageSize != null) params.pageSize = query.pageSize;
  if (query.status?.length) params.status = query.status.join(',');
  const texts: Array<[string, string | null | undefined]> = [
    ['accessionNo', query.accessionNo],
    ['patientName', query.patientName],
    ['patientId', query.patientId],
    ['patientSource', query.patientSource],
    ['cardNo', query.cardNo],
    ['examItemName', query.examItemName],
    ['applyTimeStart', query.applyTimeStart],
    ['applyTimeEnd', query.applyTimeEnd],
  ];
  for (const [key, value] of texts) {
    const trimmed = value?.trim();
    if (trimmed) params[key] = trimmed;
  }
  return params;
}

export function registerOrder(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/us/orders/${id}/register`, { method: 'POST', body: {} });
}

export function cancelOrder(id: number, reason: string): Promise<boolean> {
  return apiRequest<boolean>(`/us/orders/${id}/cancel`, { method: 'POST', body: { reason } });
}
