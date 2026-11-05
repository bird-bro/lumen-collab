/**
 * Typed API client seam.
 *
 * Clients are written against `openspec/specs/api/spec.md`; no endpoint may be
 * invented here (`specs/web-app-shell` § API Client Seam Without Invented
 * Endpoints - a gap in the API spec is raised via a proposal, never filled by a
 * guessed path). The seam is populated per resource: `client.ts` carries the
 * transport (envelope unwrap, Bearer injection, the "203" semantic), each
 * `*.ts` sibling is that resource's typed client, and `types.ts` holds the
 * shared DTOs (entity IDs are JSON numbers per ID Serialization). There is
 * no mock layer: every request goes through the proxy to the real backend
 * (`specs/web-app-shell` § Dev Server Serves Only Real Backend).
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
