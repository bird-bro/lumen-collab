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

/** The entry-category marker (us-prereg-as-registered; user ruling 2026-11-24 预约=
 * 预登记): WALK_IN 现场单 / PRE_REGISTERED 预登记 - a SERVER-stamped business fact,
 * the queue system's discriminator (never inferred from bookingTime null-ness). */
export type UsRegistrationType = 'WALK_IN' | 'PRE_REGISTERED';

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
  /** The HIS application origin (us-apply-info): the department / doctor / raw
   * `yyyy-MM-dd HH:mm:ss` moment that opened the 检查申请单 - free text, blank
   * allowed. The id anchors are RESERVED contract fields the v1 UI never sends. */
  applyDept?: string | null;
  applyDoctor?: string | null;
  applyTime?: string | null;
  applyDeptId?: string | null;
  applyDoctorId?: string | null;
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
  /** 出生日期 (yyyy-MM-dd; the archive soft-match key - us-registration-birthdate). */
  birthDate?: string | null;
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
  /** 登记人 = the create operator uid (us-prereg-as-registered: create_by serves
   * BOTH kinds - the 登记 act happens at creation for walk-ins and pre-registrations
   * alike). */
  createBy: number | null;
  /** The server-stamped entry category (us-prereg-as-registered). */
  registrationType: UsRegistrationType | null;
  /** 预登记时段锚点 (null = 现场单/无预约; 拍板 02-B). */
  bookingTime: string | null;
  pregnancyStatus: string | null;
  gestationalWeeks: number | null;
  note: string | null;
  /** The HIS application origin flattened from the ext row (us-apply-info; null
   * when the order carries no ext row). The id anchors are reserved. */
  applyDept: string | null;
  applyDoctor: string | null;
  applyTime: string | null;
  applyDeptId: string | null;
  applyDoctorId: string | null;
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

/** One granularity slot of the booking picker's day grid (us-booking-slot-picker):
 * the HH:mm bounds, the LIVE BOOKED occupancy, and the config's per-slot capacity. */
export interface SlotOptionView {
  start: string;
  end: string;
  booked: number;
  capacity: number;
}

/** The slot-options read (us-booking-slot-picker-sev): configured=false = no live
 * config for the modality (the picker disables; walk-ins unaffected). */
export interface SlotOptionsView {
  configured: boolean;
  slots: SlotOptionView[];
}

/** GET /us/orders/slot-options - the booking picker's day grid (the slot dialog's
 * 满员标灰 render; the 2026-11-24 trigger+dialog redesign - the read contract is
 * form-agnostic): the config's windows floored onto the granularity grid with the
 * create path's occupancy predicate. */
export function listSlotOptions(date: string, modality: string): Promise<SlotOptionsView> {
  return apiRequest<SlotOptionsView>('/us/orders/slot-options', {
    method: 'GET',
    query: { date, modality },
  });
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
): Promise<{
  orderId: number;
  accessionNo: string;
  status: UsOrderStatus;
  registrationType: UsRegistrationType;
  bookingTime: string | null;
}> {
  return apiRequest<{
    orderId: number;
    accessionNo: string;
    status: UsOrderStatus;
    registrationType: UsRegistrationType;
    bookingTime: string | null;
  }>('/us/orders', { method: 'POST', body });
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
