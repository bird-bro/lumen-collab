import { fetchDictItemsByName, fetchDictItemsById } from '@/api/dicts';
import type { DictItem } from '@/api/types';
import type { UsOrderStatus } from '@/api/us-orders';

/**
 * Session-cached dict-item resolver (role-type-dict-web tasks 1.2; the
 * `password-cipher.ts` shape - module-level cache, one wire call per catalog
 * per session). The FIRST consumer is the roles console's 类型 column +
 * drawer Select. Failed lookups are NOT cached - the next call retries the
 * wire (a transient backend error must not poison the session); successes
 * cache the promise so concurrent callers dedupe.
 *
 * ANCHOR DISCIPLINE (dict-lookup-by-id ruling 2026-11-19): NEW consumers look
 * up by the catalog ID (`dictItemsById` - the id never moves; the operator may
 * rename a catalog freely). `dictItemsByName` stays for the legacy role/audit
 * consumers until they migrate.
 */
const ITEMS_CACHE = new Map<string, Promise<DictItem[]>>();

/** The 「角色类型」 catalog name - the recorded cross-stack contract (V23 seed). */
export const ROLE_TYPE_DICT_NAME = '角色类型';

/** The 「审计日志类型」 catalog name - audit-log-unify D7 (V25 seed, codes 1-5 + 10/11/12). */
export const AUDIT_LOG_TYPE_DICT_NAME = '审计日志类型';

/** The 「编号类型」 catalog id - number-rule-type-dict seed V44 (id-anchored). */
export const NUMBER_TYPE_DICT_ID = 1028;

/** The 「编号格式」 (stamp pattern) catalog id - number-rule-format-dict seed V43/V45. */
export const NUMBER_STAMP_FORMAT_DICT_ID = 1029;

/**
 * The modality catalog id - dict 1024 「设备类型」, the SINGLE modality catalog
 * since the V27 unification (「检查类型」 1021 retired). ID-ANCHORED per the
 * dict-lookup-by-id ruling 2026-11-19 (the id never moves; the operator may rename
 * the catalog freely) - the setting pages resolve their 检查类型/设备类型 vocabulary
 * through `dictItemsById(MODALITY_DICT_ID)`, never by name
 * (modality-dict-id-anchor-web).
 */
export const MODALITY_DICT_ID = 1024;

/**
 * The 「性别」 catalog id - the V6 dict seed (stable rcp ca_id anchor). The stored
 * token is the dict detail's numeric `code` (data-spec ruling: gender 100401 男);
 * consumers render labels through `resolveDictLabel` with the raw-code fallback.
 */
export const GENDER_DICT_ID = 1004;

/**
 * The 「证件类型」 catalog id - the V6 dict seed (stable rcp ca_id anchor). The
 * order/archive column is a string, so the stored token is the dict `value`
 * text itself (the modality catalogs' value-keyed shape).
 */
export const ID_TYPE_DICT_ID = 1006;

/**
 * The female dict detail code - 100402 女 (the V6 seed; the data spec pins the
 * code as the stored token, e.g. gender 100401 男). Gender-conditioned UI (the
 * US 妊娠 cell) branches on this code, never on a hard-coded legacy 0/1/2.
 */
export const GENDER_FEMALE_CODE = 100402;

/**
 * The 「患者来源」 catalog id - user ruling 2026-11-24 (us-vocab-dict-catalogs
 * seed V52, reusing the dissolved 诊室类型 band slot V27 retired unseeded). The
 * stored token is the dict `value` text (the string patient_source column keeps
 * the dict text - the modality catalogs' value-keyed shape).
 */
export const PATIENT_SOURCE_DICT_ID = 1023;

/**
 * The 「妊娠状态」 catalog id - user ruling 2026-11-24 (us-vocab-dict-catalogs
 * seed V52, reusing the mislabeled rcp ORG_TYPE slot V6 excluded). The stored
 * token is the dict `value` text (ext.pregnancy_status - value-keyed).
 */
export const PREGNANCY_STATUS_DICT_ID = 1002;

/**
 * The catalog's enabled items by NAME, session-cached (dedupes concurrent
 * calls, survives route remounts within the session).
 */
export function dictItemsByName(catalogName: string): Promise<DictItem[]> {
  const cached = ITEMS_CACHE.get(catalogName);
  if (cached) return cached;

  const request = fetchDictItemsByName(catalogName).catch((error: unknown) => {
    ITEMS_CACHE.delete(catalogName);
    throw error;
  });
  ITEMS_CACHE.set(catalogName, request);
  return request;
}

/**
 * The catalog's enabled items by ID, session-cached (the id-anchored resolver -
 * the ruling shape, one wire call per catalog per session; dedupes concurrent
 * calls, survives route remounts).
 */
export function dictItemsById(catalogId: number): Promise<DictItem[]> {
  const key = `id:${catalogId}`;
  const cached = ITEMS_CACHE.get(key);
  if (cached) return cached;

  const request = fetchDictItemsById(catalogId).catch((error: unknown) => {
    ITEMS_CACHE.delete(key);
    throw error;
  });
  ITEMS_CACHE.set(key, request);
  return request;
}

/**
 * Drops every cached catalog (the `password-cipher.invalidateKeyCache` shape -
 * one self-heal step for the caller). Production never calls it; unit tests do,
 * so a suite's mocked lookups cannot leak into the next suite's session cache.
 */
export function invalidateDictItemsCache(): void {
  ITEMS_CACHE.clear();
}

/**
 * The display label for a stored code: the dict `value` matched by `code`,
 * falling back to the RAW number when the dictionary lacks the entry or never
 * loaded (coordination D4 - the page never renders a blank cell).
 */
export function resolveDictLabel(items: DictItem[], code: number): string {
  const match = items.find((item) => item.code === code);
  return match?.value ?? String(code);
}

/**
 * The display label for a stored VALUE-keyed token: the modality catalogs
 * (设备类型/检查类型, org-device-room ruling 2026-11-12) make the dict `value`
 * itself the machine token ('CT'/'DR' - business matching joins on it), so the
 * stored row text matches `item.value` rather than the numeric admin `code`.
 * Falls back to the RAW token when the dictionary lacks the entry or never
 * loaded (same never-a-blank-cell rule as `resolveDictLabel`).
 */
export function resolveDictLabelByValue(items: DictItem[], value: string): string {
  const match = items.find((item) => item.value === value);
  return match?.value ?? value;
}

/**
 * The 「登记单状态」 catalog id - the V55 seed (registration-order-status-dict-sev;
 * user ruling 2026-11-26). ID-ANCHORED per the dict-lookup-by-id ruling 2026-11-19
 * (the id never moves; the operator may rename the catalog freely). LABEL-ONLY
 * vocabulary: the stored/wire status token stays the backend enum name; the dict
 * supplies the desk's chip/state-tag labels through the code bridge below.
 */
export const US_ORDER_STATUS_DICT_ID = 1031;

/**
 * The enum-name ↔ dict-code bridge (the recorded cross-stack contract in
 * openspec/coordination/registration-order-status-dict.md): code = the
 * catalog-id-prefixed family 103101-103106 (catalId*100 + seq — the 100401 性别
 * anchor shape, the user's 2026-11-26 correction superseding the draft's bare 1-6),
 * `fallback` = the 中文 label rendered when the dict item is missing/disabled or
 * the fetch failed (the desk meter must survive a dict outage - the coordination
 * doc's deliberate deviation from the form vocabularies' empty-options degrade).
 * CONTRACT, not a vocabulary option set (the Dict-Resolved Vocabularies carve-out
 * in the delta spec).
 */
export const US_ORDER_STATUS_BRIDGE: Record<
  UsOrderStatus,
  { code: number; fallback: string }
> = {
  REGISTERED: { code: 103101, fallback: '已登记' },
  IN_EXAM: { code: 103102, fallback: '检查中' },
  IN_DIAGNOSIS: { code: 103103, fallback: '诊断中' },
  COMPLETED: { code: 103104, fallback: '已完成' },
  PRINTED: { code: 103105, fallback: '已打印' },
  CANCELLED: { code: 103106, fallback: '已取消' },
};

/** One resolved status-label row: the enum token, its bridge code, the rendered label
 * and the DB-configured color token (`tag`, null when unconfigured/fallback). */
export interface UsOrderStatusLabel {
  status: UsOrderStatus;
  code: number;
  label: string;
  /** The dict-`tag` color token as configured in 字典管理 (e.g. 'blue'/'amber'); the
   * page maps it through the design-token hue map. Null = no tag / bridge fallback. */
  hue: string | null;
  /** True when the label came off the wire (dict value); false = bridge fallback. */
  fromDict: boolean;
}

/**
 * The six statuses in bridge-code order with their FALLBACK labels - the desk
 * meter's degradation floor (never blocks, never blanks).
 */
export function usOrderStatusFallbackLabels(): UsOrderStatusLabel[] {
  return (Object.keys(US_ORDER_STATUS_BRIDGE) as UsOrderStatus[]).map((status) => ({
    status,
    code: US_ORDER_STATUS_BRIDGE[status].code,
    label: US_ORDER_STATUS_BRIDGE[status].fallback,
    hue: null,
    fromDict: false,
  }));
}

/**
 * The status labels resolved through the id-anchored lookup (session-cached by
 * `dictItemsById`): the statuses whose dict items are enabled render the dict
 * `value` IN DICT ORDER (the backend returns items sort-ordered - the chip order
 * follows it), any status whose item is missing/disabled falls back to the bridge
 * label appended in code order, and the six statuses ALWAYS render (the meter is
 * never blank). Throws when the dict fetch fails - the PAGE owns the one error
 * message + the `usOrderStatusFallbackLabels` degradation (this resolver never
 * swallows: a swallowed failure would silently mask the backend, ruling 2026-11-05).
 */
export async function usOrderStatusLabels(): Promise<UsOrderStatusLabel[]> {
  const items = await dictItemsById(US_ORDER_STATUS_DICT_ID);
  const codeToStatus = new Map<number, UsOrderStatus>(
    (Object.keys(US_ORDER_STATUS_BRIDGE) as UsOrderStatus[]).map((status) => [
      US_ORDER_STATUS_BRIDGE[status].code,
      status,
    ]),
  );
  const resolved: UsOrderStatusLabel[] = [];
  const seen = new Set<UsOrderStatus>();
  for (const item of items) {
    const status = item.code != null ? codeToStatus.get(item.code) : undefined;
    if (!status) continue;
    seen.add(status);
    resolved.push({
      status,
      code: US_ORDER_STATUS_BRIDGE[status].code,
      label: item.value ?? US_ORDER_STATUS_BRIDGE[status].fallback,
      hue: item.tag || null,
      fromDict: item.value != null,
    });
  }
  // the uncovered statuses ride the bridge floor, in code order after the dict block
  for (const label of usOrderStatusFallbackLabels()) {
    if (!seen.has(label.status)) resolved.push(label);
  }
  return resolved;
}
