/**
 * Identifier type for every entity ID carried in API payloads.
 *
 * Entity IDs are snowflake `bigint`s (application-layer generated). Per
 * `openspec/specs/api/spec.md` § ID Serialization they MUST be serialized as
 * JSON strings: snowflake values exceed `Number.MAX_SAFE_INTEGER` and lose
 * precision as JS numbers. ID-bearing DTOs therefore type identifiers as
 * `string`, never `number`.
 */
export type EntityId = string;
