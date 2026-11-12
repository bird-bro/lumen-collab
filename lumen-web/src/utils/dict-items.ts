import { fetchDictItemsByName } from '@/api/dicts';
import type { DictItem } from '@/api/types';

/**
 * Session-cached dict-item resolver (role-type-dict-web tasks 1.2; the
 * `password-cipher.ts` shape - module-level cache, one wire call per catalog
 * name per session). The FIRST consumer is the roles console's 类型 column +
 * drawer Select; promote the catalog names to a shared constants module when a
 * second pattern emerges (coordination D4). Failed lookups are NOT cached - the
 * next call retries the wire (a transient backend error must not poison the
 * session); successes cache the promise so concurrent callers dedupe.
 */
const ITEMS_CACHE = new Map<string, Promise<DictItem[]>>();

/** The 「角色类型」 catalog name - the recorded cross-stack contract (V23 seed). */
export const ROLE_TYPE_DICT_NAME = '角色类型';

/** The 「审计日志类型」 catalog name - audit-log-unify D7 (V25 seed, codes 1-5 + 10/11/12). */
export const AUDIT_LOG_TYPE_DICT_NAME = '审计日志类型';

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
