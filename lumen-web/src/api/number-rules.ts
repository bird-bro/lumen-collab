/**
 * Platform number-rule typed client (number-rule-unification-web task 1.2; the
 * number-rule-unification-sev backend surface). The rule directory moved from the
 * patient service to the SYSTEM service - this client replaces the retired
 * patient.ts rule section (`patient:rule:*` → `system:number:*`).
 *
 * Backend surface: GET/POST `/system/number-rules`, PUT/DELETE `/{id}` under the
 * `system:number:*` permission family. `serialCur`/`stampValue` are runtime state -
 * no CRUD body ever carries them; the list echoes `serialCur` as the 当前流水 column.
 */
import { apiRequest } from './client';

/** One rule row (the platform `number_rule` entity's page surface). */
export interface NumberRule {
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
export interface NumberRulePage {
  records: NumberRule[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** Page query - paging only (the v1 surface carries no filter fields). */
export interface NumberRulePageQuery {
  pageNum?: number;
  pageSize?: number;
}

/** Create body - `serialCur`/`stampValue` are runtime state, never sent. */
export interface CreateNumberRuleBody {
  ruleType: string;
  prefix?: string | null;
  suffix?: string | null;
  serialLength: number;
  isStamp: number;
  /** Null + isStamp=1 defaults server-side to `yyMMdd`. */
  stamp?: string | null;
  active: number;
}

/** Full-surface update body (same shape as create; the ruleType is immutable). */
export interface UpdateNumberRuleBody extends CreateNumberRuleBody {}

/**
 * GET /system/number-rules - the 号码规则 table's server-side page; falls back to
 * the paging defaults (pageNum=1, pageSize=20).
 */
export function pageNumberRules(query: NumberRulePageQuery): Promise<NumberRulePage> {
  return apiRequest<NumberRulePage>('/system/number-rules', {
    method: 'GET',
    query: {
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
    },
  });
}

/** POST /system/number-rules - create one rule (duplicate active family → A0714). */
export function createNumberRule(body: CreateNumberRuleBody): Promise<number> {
  return apiRequest<number>('/system/number-rules', { method: 'POST', body });
}

/** PUT /system/number-rules/{id} - full-surface update (the ruleType is immutable). */
export function updateNumberRule(id: number, body: UpdateNumberRuleBody): Promise<boolean> {
  return apiRequest<boolean>(`/system/number-rules/${id}`, { method: 'PUT', body });
}

/** DELETE /system/number-rules/{id} - logical delete (active rules refuse first). */
export function deleteNumberRule(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/system/number-rules/${id}`, { method: 'DELETE' });
}
