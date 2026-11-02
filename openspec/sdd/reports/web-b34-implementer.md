# Implementer Report - init-web-skeleton Batches 3-4 (tasks 3.1-4.3), lumen-web

- **Status**: DONE
- **Commit**: `3cfacbe47276e69908a0d6abe29a21f6fafd89bc` - `✨ feat(web): Batch 3-4 - typed API seam (no endpoints) + unified-entry proxy (init-web-skeleton 3.1-4.3)` (4 files, 50 insertions, 4 deletions)
- **Environment**: Node v22.16.0, pnpm 10.32.1; no test runner per `verify.config.yaml` (`test: null`, L3 SKIP) - verification is the strict TS build + inspection commands, plus one type-level negative control (same method as B1's alias probe)

## 1. Envelope field table vs spec (task 3.1)

`lumen-web/src/api/envelope.ts` created verbatim from the brief; exported from the module index alongside `BASE_PATH`.

| Field | TS type (`ApiEnvelope<T>`) | Spec value (`openspec/changes/init-backend-skeleton/specs/api/spec.md`, OPS `R` contract) | Match |
|:--|:--|:--|:--|
| `timestamp` | `string` | server time formatted `yyyy-MM-dd hh:mm:ss.SSS` | yes |
| `code` | `string` | string; `"200"` success-with-content, `"204"` success-no-payload, error code strings (Error Code Scheme / A-B-C letter codes) | yes - string, never number |
| `msg` | `string` | human-readable description of the result/failure | yes |
| `data` | `T \| null` | typed payload on success; **null** on 204 and on error (delta-spec scenarios) | yes |

Note: the delta spec's prose shape shows `data: {}` (the success payload); the null-on-204/error behavior comes from its own scenarios, and tasks.md 3.1 + design D4 fix the type as `T | null`. `BASE_PATH = '/api/v1' as const` exported from `src/api/index.ts` (design D4: both exported from the module index).

### Negative control (type-level red, temporary scratch - deleted, never committed)

- `src/api/envelope.scratch.ts` assigning `code: 200` (number) -> `error TS2322: Type 'number' is not assignable to type 'string'`, `tsc --noEmit` exit **2**.
- Scratch deleted -> `tsc --noEmit` exit **0**. The seam provably rejects a numeric `code`.

## 2. Task 3.2 - EntityId alias

`lumen-web/src/api/entity-id.ts`: `export type EntityId = string;` with a doc comment citing `openspec/specs/api/spec.md` § ID Serialization (snowflake bigints exceed `Number.MAX_SAFE_INTEGER`, JSON strings mandated). Verify grep:

```
$ grep -n "EntityId" lumen-web/src/api/*.ts
lumen-web/src/api/entity-id.ts:10:export type EntityId = string;
lumen-web/src/api/index.ts:12:export type { EntityId } from './entity-id';
```

Alias is `string`, not `number`.

## 3. Task 3.3 - no invented endpoints (grep evidence, verbatim)

```
$ grep -rn "api/v1/" lumen-web/src --include='*.ts' --include='*.tsx'
(no output - exit 1)
```

Zero matches: no resource-specific path exists anywhere under `src/`. The specified pattern requires a trailing slash, and `BASE_PATH` is the bare prefix `'/api/v1'`, so it does not self-match. Positive pin that the prefix family appears exactly once, as the constant:

```
$ grep -rn "api/v1" lumen-web/src --include='*.ts' --include='*.tsx'
lumen-web/src/api/index.ts:20:export const BASE_PATH = '/api/v1' as const;
```

Exactly one occurrence - the `BASE_PATH` constant. Intent Lock intact; the seam is deliberately unpopulated (design D4).

## 4. Task 4.1 - proxy config (vite.config.ts)

```ts
server: {
  proxy: {
    '/api/v1': {
      target: process.env.VITE_API_ENTRY_URL ?? 'http://localhost:80',
      changeOrigin: true,
    },
  },
},
```

Target = the **nginx unified entry, port 80**, per `openspec/coordination/init-project-skeleton.md` port table ("nginx (unified API entry + SPA static) | 80 / 443 | the only inbound route; there is **no Java gateway**"; "`lumen-web` dev server | 5173 | proxies `/api/v1` to the nginx entry"). Env override via `VITE_API_ENTRY_URL`; `process.env` type-checks because `tsconfig.node.json` has `"types": ["node"]` and includes `vite.config.ts` (B1 setup).

## 5. Task 4.2 - no hard-coded host/port (grep evidence, verbatim)

```
$ grep -rnE "localhost:80[0-9][0-9]|127\.0\.0\.1" lumen-web/src lumen-web/vite.config.ts
(no output - exit 1)
```

Zero matches - "at most the nginx default" is satisfied with zero (the regex targets 4-digit ports, `80xx`, so the port-80 default cannot match it by construction). Supplementary pin that the only host literal in scope is the allowed default itself:

```
$ grep -rn "localhost" lumen-web/src lumen-web/vite.config.ts
lumen-web/vite.config.ts:20:        target: process.env.VITE_API_ENTRY_URL ?? 'http://localhost:80',
```

Nothing under `src/`.

## 6. Task 4.3 - dev server starts and proxy forwards (two rungs)

**Rung A - default target, backend absent.** Pre-check `lsof -nP -i :80` -> empty (nothing on port 80). Dev server started clean:

```
  VITE v7.3.7  ready in 65 ms
  ➜  Local:   http://localhost:5173/
```

`curl -s -o /dev/null -w "%{http_code}" http://localhost:5173/api/v1/nonexistent` -> **500** (an HTTP answer from the dev server, not a refused connection at 5173), and the log shows the proxy taking the request and failing to reach the target - exactly the acceptable ECONNREFUSED form:

```
9:46:56 AM [vite] http proxy error: /api/v1/nonexistent
AggregateError [ECONNREFUSED]:
```

**Rung B - controlled forwarding proof (env var + arrival at target).** Dummy HTTP listener on `127.0.0.1:9999`, dev server started with `VITE_API_ENTRY_URL=http://localhost:9999`, same probe:

```
$ curl -s http://localhost:5173/api/v1/nonexistent
dummy-entry-ok
```

Dummy listener log (the request physically arrived at the configured target):

```
DUMMY-ENTRY-LISTENING-9999
HIT GET /api/v1/nonexistent
```

Together: the proxy is registered, `/api/v1/*` is forwarded to the `VITE_API_ENTRY_URL` target, and rung A shows the default (`http://localhost:80`) is what is wired when the env var is unset (connection refused against the empty port 80, surfaced as the dev server's proxied error). Both servers killed after capture (exit 143 = SIGTERM; no orphans on 5173/9999).

## 7. Final verification

| Command | Exit | Note |
|:--|:--|:--|
| `pnpm --dir lumen-web build` | 0 | full gate `tsc --noEmit && tsc -p tsconfig.node.json --noEmit && vite build`; re-run after all changes |
| `pnpm --dir lumen-web exec tsc --noEmit` | 0 | strict clean on final tree |

## 8. Files delivered (commit `3cfacbe`)

- `lumen-web/src/api/envelope.ts` (new) - `ApiEnvelope<T>` verbatim from the brief
- `lumen-web/src/api/entity-id.ts` (new) - `EntityId = string` alias + ID-Serialization citation
- `lumen-web/src/api/index.ts` (modified) - placeholder replaced by the module index: re-exports + `BASE_PATH`
- `lumen-web/vite.config.ts` (modified) - `server.proxy` block only; plugins/alias untouched

`openspec/sdd/progress.md` (orchestrator's edit) left uncommitted per "commit only lumen-web/**".

## 9. Deviations / notes (with reasons)

1. **3.3's literal grep returns zero lines, not one** - the `api/v1/` pattern needs a trailing slash and `BASE_PATH` is the bare prefix. Zero resource-specific paths satisfies the requirement ("only the BASE_PATH constant, with no resource-specific path"); the bare-prefix grep in section 3 provides the positive one-line pin. Same reasoning applies to 4.2 (regex matches only 4-digit ports; zero matches + sole-localhost supplementary grep).
2. **Comments avoid the `api/v1/` byte sequence on purpose** - so the 3.3 grep output stays exactly the BASE_PATH constant; the doc comments reference "the `/api/v1` base path" instead.
3. **Vite 7 prints no proxy table at startup** - evidence form used: startup log (clean start) + proxied-error excerpt naming `/api/v1/nonexistent` (rung A) + physical arrival at a controlled listener (rung B) + config inspection (section 4). Stronger than a printed table would be.
4. **Scope discipline**: no endpoint constant, no HTTP client (fetch/axios), no mock tool wired, no test runner added, no `any`, no strictness weakening; `lumen-sev` untouched (seam is lumen-sev-agnostic - type-only, no backend imports).
