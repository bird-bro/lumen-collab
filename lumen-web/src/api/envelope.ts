/**
 * Typed response envelope - mirrors the OPS `R` contract adopted by
 * openspec/specs/api/spec.md (init-backend-skeleton aligned it): a string `code`,
 * human-readable `msg`, server `timestamp`, and the typed payload.
 * `code` is a STRING - clients compare against "200"/"204" or letter codes, never numbers.
 */
export interface ApiEnvelope<T> {
  timestamp: string;
  code: string;
  msg: string;
  data: T | null;
}
