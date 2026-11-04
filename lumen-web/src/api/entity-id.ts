/**
 * Identifier type for every entity ID carried in API payloads.
 *
 * Entity IDs are PostgreSQL Integer auto-increment values (user decision
 * 2026-11-02, OPS/随访 lineage - the former snowflake-bigint rule was
 * withdrawn). Per `openspec/specs/api/spec.md` § ID Serialization they
 * serialize as JSON numbers; the former `string` typing (snowflake-era) is
 * withdrawn and typed `number`, never `string`.
 */
export type EntityId = number;
