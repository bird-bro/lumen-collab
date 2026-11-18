/**
 * Patient-domain typed clients (patient-domain task 5.1) - the 号码规则 console
 * page's transport. SELF-CONTAINED module (types + client in one file): the
 * house convention keeps shared types in `./types`, but that file is currently
 * carrying a parallel session's uncommitted exam-item work - a patient section
 * there would couple two changes' commits. When the parallel change archives,
 * the types MAY migrate to `./types` for uniformity; the client seam is
 * identical either way (BASE_PATH + apiRequest envelope contract).
 *
 * Backend surface (patient-domain b4, `PatientNumberRuleController`):
 * GET/POST `/patient/patient-number-rules`, PUT/DELETE `/{id}` under the
 * `patient:rule:*` permission family. `serialCur`/`stampValue` are runtime
 * state - no CRUD body ever carries them; the list echoes `serialCur` as the
 * 当前流水 column.
 */
import { apiRequest } from './client';

/** One rule row (the `patient_number_rule` entity's page surface). */
export interface PatientNumberRule {
  id: number;
  ruleType: string;
  prefix: string | null;
  suffix: string | null;
  serialLength: number;
  serialCur: number;
  /** 1 = the stamp segment is on (`stamp` holds the Java date pattern). */
  isStamp: number;
  stamp: string | null;
  /** 1 = the active issuance source (exactly one per ruleType). */
  active: number;
  createTime: string;
}

/** MyBatis-Plus `IPage` JSON shape (records/total/size/current/pages). */
export interface PatientNumberRulePage {
  records: PatientNumberRule[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** Page query - v1 carries NO filter fields, paging only (b4 decision 5). */
export interface PatientNumberRulePageQuery {
  pageNum?: number;
  pageSize?: number;
}

/** Create body - `serialCur`/`stampValue` are runtime state, never sent. */
export interface CreatePatientNumberRuleBody {
  ruleType: string;
  prefix?: string | null;
  suffix?: string | null;
  serialLength: number;
  isStamp: number;
  /** Null + isStamp=1 defaults server-side to `yyMMdd` (b4 decision 5). */
  stamp?: string | null;
  active: number;
}

/** Full-surface update body (same shape as create; D9-style null-aware patch). */
export interface UpdatePatientNumberRuleBody extends CreatePatientNumberRuleBody {}

/**
 * GET /patient/patient-number-rules - the 号码规则 table's server-side page;
 * falls back to the paging defaults (pageNum=1, pageSize=20).
 */
export function pageNumberRules(query: PatientNumberRulePageQuery): Promise<PatientNumberRulePage> {
  return apiRequest<PatientNumberRulePage>('/patient/patient-number-rules', {
    method: 'GET',
    query: {
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
    },
  });
}

/** POST /patient/patient-number-rules - returns the new rule id. */
export function createNumberRule(body: CreatePatientNumberRuleBody): Promise<number> {
  return apiRequest<number>('/patient/patient-number-rules', { method: 'POST', body });
}

/** PUT /patient/patient-number-rules/{id} - full-surface update. */
export function updateNumberRule(id: number, body: UpdatePatientNumberRuleBody): Promise<boolean> {
  return apiRequest<boolean>(`/patient/patient-number-rules/${id}`, { method: 'PUT', body });
}

/** DELETE /patient/patient-number-rules/{id} - active rule refused A0400 server-side. */
export function deleteNumberRule(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/patient/patient-number-rules/${id}`, { method: 'DELETE' });
}
