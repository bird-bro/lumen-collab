# WHEN/THEN Evidence Walk (task 5.1) - input to /opsx:verify L2

Each scenario from the two delta specs, with the evidence produced and independently
re-verified during batches 1-4 (all suite/db evidence from the shared team test server,
i.e. the real stack database; reviewer re-runs cited per batch).

## specs/system-data/spec.md

1. **Log table exists on fresh database** (partitioned end-state) - VERIFIED.
   V13 builds both parents `PARTITION BY RANGE (date_ym)`; live check: relkind='p',
   partkey `RANGE (date_ym)`, exactly 6 children (2026/2027/def ×2) with the file's exact
   bounds; convergent preamble verified for BOTH fresh-DB and re-run paths (b1 review).
2. **Rows survive the conversion** - VERIFIED. Single-tx copy-swap, explicit column lists;
   live row counts monotonic through the fix cycle + suite traffic (287/277 incl. real
   login writes during b1 review), min_id=1 preserved.
3. **Write into an unpartitioned year lands in the default partition** - VERIFIED
   (structural). DEFAULT partitions exist on both tables; upper bounds exclusive so any
   2028xx routes to `*_def` (PG partition routing; no live insert per read-only psql
   discipline - accepted by b1 review).
4. **Query sign logs** (newest-first, account/uid/dateYm AND-filters, NO token field) -
   VERIFIED. `AuthSignLogQueryTests` keyed assertions with REAL token values seeded and
   per-row JSON key-absence (`item.has("token")`); clamp edge (size=100), empty page,
   blank-account unfiltered all pinned; b2 reviewer re-ran 74/74.
5. **Sign-log permission is enforced server-side** (independent of `system:log:list`) -
   VERIFIED. Both directions pinned: log-only principal → A0301 on `/sign`; sign-only
   principal → 200 on `/sign` AND A0301 on `/logs` (method-level override, D4); menu 17
   carries `system:signlog:list` live with the role-1 grant (b3 review).

## specs/web-system-console/spec.md

6. **Filtered sign-log query** - VERIFIED (unit seam). `sign-logs-page.test.tsx`:
   filters flow into `listSignLogs` (numeric uid/dateYm conversion), reset re-queries,
   server-side paging via pagination click, newest-first asserted by DOM order; 167/167
   green + build exit 0 (b4 reviewer re-ran).
7. **Read-only surface** - VERIFIED. Tests assert zero mutation controls; reviewer grep
   clean; `usePerm()` mirrors the logs page.
8. **Menu-driven visibility follows the page permission** - VERIFIED server-side + seeded:
   menu 17 live (exact tuple, deleted 0) under 安全 16 with role-1 grant; frontend menu
   rendering rides the existing auth-rbac /me perms machinery (auth-rbac spec scenario
   "Button permission in the perm set" family).
   ⚠ LIVE BROWSER CONFIRMATION PENDING - the contract's review gate ("user confirms the
   登录日志 page against the real backend before archive"): user walks 安全 → 登录日志 in
   the running dev stack, checks columns/rows against real `log_sign` data. This is the
   user-acceptance step of /opsx:verify, not outstanding implementation work.

## Build/test credentials at close (L1/L3)

- Backend: `./mvnw test -q` 74/74 × 3 independent runs (b2 reviewer, b3 implementer ×2,
  b3 reviewer ×2) - JDK 11, Flyway 13+14 success, checksum-validated.
- Web: `pnpm test` 167/167 + `pnpm build` exit 0 (b4 implementer + reviewer re-run).
