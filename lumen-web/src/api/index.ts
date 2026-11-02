/**
 * Typed API client seam.
 *
 * Clients are written against `openspec/specs/api/spec.md`; no endpoint may be
 * invented here (`specs/web-app-shell` § API Client Seam Without Invented
 * Endpoints - a gap in the API spec is raised via a proposal, never filled by a
 * guessed path). The seam is deliberately unpopulated until the API spec gains
 * its first concrete endpoint; the mock layer (tool deferred, see debt.md)
 * plugs in here too.
 */
export type { ApiEnvelope } from './envelope';
export type { EntityId } from './entity-id';

/**
 * Base URL of the unified API entry (nginx) - see `openspec/specs/api/spec.md`
 * § Gateway Path Convention. Every documented endpoint lives under this base
 * path suffixed by a service segment; clients join resource paths onto this
 * constant and never target a service port directly.
 */
export const BASE_PATH = '/api/v1' as const;
