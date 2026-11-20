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
}

/** The jsonb patient snapshot the kernel carries on the order (display projection). */
export interface UsPatientSnapshot {
  patId?: string | null;
  name?: string | null;
  gender?: number | null;
  phone?: string | null;
  age?: string | null;
  [key: string]: unknown;
}

/** The us_registration_ext extension block (decision #5: 孕检归 us 域). */
export interface UsRegistrationExtBody {
  pregnancyStatus?: string | null;
  gestationalWeeks?: number | null;
  note?: string | null;
}

/** The demographics-or-identity create body (the server resolves-or-registers). */
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
  pregnancyStatus: string | null;
  gestationalWeeks: number | null;
  note: string | null;
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

export function createOrder(body: UsOrderCreateBody): Promise<{ orderId: number; accessionNo: string }> {
  return apiRequest<{ orderId: number; accessionNo: string }>('/us/orders', { method: 'POST', body });
}

export function pageOrders(query: { pageNum: number; pageSize: number }): Promise<UsOrderPage> {
  return apiRequest<UsOrderPage>('/us/orders', { query });
}

export function registerOrder(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/us/orders/${id}/register`, { method: 'POST', body: {} });
}

export function cancelOrder(id: number, reason: string): Promise<boolean> {
  return apiRequest<boolean>(`/us/orders/${id}/cancel`, { method: 'POST', body: { reason } });
}
