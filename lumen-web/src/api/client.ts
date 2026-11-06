/**
 * Thin fetch transport client (batch 4 of web-foundation-auth-console, task
 * 4.1; design D4). Deliberately dependency-free - the envelope types are ours
 * (see `./envelope`), so no HTTP library is pulled in.
 *
 * Dependency-injection seam: `configureApiClient` receives the token getter
 * and the session-expiry callback from the auth store (`src/stores/auth.ts`)
 * when that module initializes. This module MUST NOT import the store - the
 * store imports this one, and the callbacks keep the dependency edge one-way
 * (no cycle).
 *
 * Envelope contract (openspec/specs/api/spec.md, openspec/specs/errors/spec.md):
 * - body code "200" -> resolve `data`; code "204" -> resolve null;
 * - any other code -> throw `ApiError{code, msg}` (A/B-class business codes);
 * - code "203" (the AuthenticationEntryPointImpl transport-auth semantic)
 *   arrives as HTTP 200 with the code in the BODY, so this client keys on the
 *   envelope code and NEVER on the HTTP status: "203" fires
 *   `onSessionExpired()` and then still throws, so callers cannot swallow it.
 */
import type { ApiEnvelope } from './envelope';
import { BASE_PATH } from './index';

/**
 * B-class transport code for failures below the envelope layer (network
 * failure, non-JSON body, non-envelope body). Chosen value: "B0001", the
 * errors-spec system-error code - the closest documented B-class semantic.
 */
const TRANSPORT_ERROR_CODE = 'B0001';

/** Error carrying the envelope `code` + `msg` - A/B-class codes and "203" alike. */
export class ApiError extends Error {
  readonly code: string;
  readonly msg: string;

  constructor(code: string, msg: string) {
    super(msg);
    this.name = 'ApiError';
    this.code = code;
    this.msg = msg;
  }
}

/** Store-provided callbacks injected once via `configureApiClient`. */
export interface ApiClientConfig {
  /** Current session token, or null when logged out (no Authorization header is sent). */
  getToken?: () => string | null;
  /** Invoked exactly when an envelope code "203" arrives (session-clear + redirect belong to the store/guard side). */
  onSessionExpired?: () => void;
}

let getToken: () => string | null = () => null;
let onSessionExpired: () => void = () => undefined;

/**
 * In-flight coalescing for idempotent reads. React StrictMode intentionally
 * remounts effects in development, so two identical GETs can start before the
 * first response arrives. Share that one transport Promise, then forget it:
 * completed requests are never cached, so refreshes still read fresh data.
 */
const GET_REQUESTS_IN_FLIGHT = new Map<string, Promise<unknown>>()

/** Prevents a pre-mutation read from being shared with a post-mutation reader. */
function clearInFlightGetRequests(): void {
  GET_REQUESTS_IN_FLIGHT.clear()
}

/**
 * Wires the store-provided callbacks (module-level no-op defaults before the
 * first call). Each provided key overwrites its slot; omitted keys keep the
 * previously configured one.
 */
export function configureApiClient(config: ApiClientConfig): void {
  if (config.getToken) getToken = config.getToken;
  if (config.onSessionExpired) onSessionExpired = config.onSessionExpired;
}

export interface ApiRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  /** JSON request body - serialized with Content-Type: application/json when present. */
  body?: unknown;
  /**
   * Query parameters appended to the joined path (null/undefined entries are
   * skipped, values URL-encoded). Clients pass documented query keys here so
   * no URL is ever assembled outside this client.
   */
  query?: Record<string, string | number | boolean | null | undefined>;
}

function buildQueryString(query: NonNullable<ApiRequestOptions['query']>): string {
  const pairs = Object.entries(query)
    .filter(([, value]) => value !== null && value !== undefined)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
  return pairs.length > 0 ? `?${pairs.join('&')}` : '';
}

function describeCause(cause: unknown): string {
  return cause instanceof Error ? cause.message : String(cause);
}

/**
 * The shared envelope contract, transport-agnostic (org-logo-upload-web task 1.2
 * extracted it from `performApiRequest` so the multipart transport semantics stay
 * identical): body code "200" -> resolve `data`; "204" -> resolve null; "203" fires
 * `onSessionExpired()` then still throws; any other code throws `ApiError{code,msg}`.
 */
async function unwrapEnvelope<T>(response: Response, path: string): Promise<T> {
  let envelope: ApiEnvelope<T>;
  try {
    envelope = (await response.json()) as ApiEnvelope<T>;
  } catch (cause) {
    throw new ApiError(TRANSPORT_ERROR_CODE, `non-JSON response from ${path}: ${describeCause(cause)}`);
  }
  if (envelope === null || typeof envelope !== 'object' || typeof envelope.code !== 'string') {
    const raw = JSON.stringify(envelope) ?? String(envelope);
    throw new ApiError(TRANSPORT_ERROR_CODE, `response from ${path} is not an ApiEnvelope: ${raw.slice(0, 200)}`);
  }

  if (envelope.code === '200') return envelope.data as T;
  if (envelope.code === '204') return null as T;
  if (envelope.code === '203') {
    onSessionExpired();
    throw new ApiError(envelope.code, envelope.msg);
  }
  throw new ApiError(envelope.code, envelope.msg);
}

/**
 * Performs one transport call without read coalescing. See `apiRequest` for
 * the envelope contract.
 */
async function performApiRequest<T>(path: string, options: ApiRequestOptions): Promise<T> {
  const url = `${BASE_PATH}${path}${options.query ? buildQueryString(options.query) : ''}`;

  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  const init: RequestInit = { method: options.method ?? 'GET', headers };
  if (options.body !== undefined) init.body = JSON.stringify(options.body);

  let response: Response;
  try {
    response = await fetch(url, init);
  } catch (cause) {
    throw new ApiError(TRANSPORT_ERROR_CODE, `network failure calling ${path}: ${describeCause(cause)}`);
  }

  return unwrapEnvelope<T>(response, path);
}

/**
 * Public request entry: performs the envelope contract above and coalesces
 * concurrent identical GET calls only. The key includes the token so requests
 * from different sessions never share a response; removal on settlement keeps
 * later calls fresh rather than introducing a result cache.
 */
export function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  if (method !== 'GET') {
    clearInFlightGetRequests();
    return performApiRequest<T>(path, options);
  }

  const url = `${BASE_PATH}${path}${options.query ? buildQueryString(options.query) : ''}`;
  const key = JSON.stringify({ url, token: getToken(), body: options.body ?? null });
  const pending = GET_REQUESTS_IN_FLIGHT.get(key);
  if (pending) return pending as Promise<T>;

  const request = performApiRequest<T>(path, options);
  const tracked = request.finally(() => {
    if (GET_REQUESTS_IN_FLIGHT.get(key) === tracked) GET_REQUESTS_IN_FLIGHT.delete(key);
  });
  GET_REQUESTS_IN_FLIGHT.set(key, tracked);
  return tracked;
}

/**
 * The multipart upload transport (org-logo-upload-web task 1.2; spec `web-app-shell`
 * § Multipart Upload Transport): wraps ONE `File` in `FormData` under the documented
 * part name, sends the Bearer header, and deliberately sets NO `Content-Type` - the
 * browser owns the multipart boundary. Envelope semantics are the shared ones
 * (`unwrapEnvelope`): only endpoints documented in the api spec may be called through
 * it - the invented-endpoints rule applies unchanged. Like any mutation it drops the
 * in-flight GET coalescing so no pre-upload read leaks into a post-upload reader.
 */
export async function apiUpload<T>(path: string, file: File, partName = 'file'): Promise<T> {
  const url = `${BASE_PATH}${path}`;

  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  const body = new FormData();
  body.append(partName, file);

  let response: Response;
  try {
    response = await fetch(url, { method: 'POST', headers, body });
  } catch (cause) {
    throw new ApiError(TRANSPORT_ERROR_CODE, `network failure calling ${path}: ${describeCause(cause)}`);
  }

  clearInFlightGetRequests();
  return unwrapEnvelope<T>(response, path);
}
