# Design

## Context

Brainstorm 2026-11-12 (RCP `/setting/*` readout → rulings, full ledger in
`openspec/coordination/exam-setting.md`): standalone `lumen-setting` service, new
`setting` schema, six tables, two-scope report templates, modality vocabulary reused
from dict 1021. Backend precedents to follow: `org-device-room-api` (table/migration/
test shape), `AuthDeviceRoom*` test family, OrgDeviceController verb style.

## Goals / Non-Goals

- Goals: the catalog + template dictionary layer exists, hospital-wide, on the house
  conventions, testable with zero infrastructure.
- Non-Goals: booking-side consumption APIs, HIS/platform mapping, print templates,
  performance calculation, 科室-scope templates, MQ events, Feign contracts.

## Decisions

### D1: Module shape (naming.md service-package-layout)

`lumen-sev/lumen-setting` executable jar, `org.birdbro.lumen.setting.SettingApplication`
scanning `org.birdbro`; layering `controller/service/mapper/entity/dto`; catalog
services are concrete classes (MenuService precedent) — no interface+impl ceremony;
application class in the service root package. `pom.xml` inherits `lumen-bom` versions
1:1 (no version edits). Aggregator gains `<module>lumen-setting</module>`.

### D2: Splits and joins (what RCP got wrong, kept fixed)

- Group and body are separate tables (brainstorm Option A): `exam_body.group_id`
  anchors the group, `up_id` nests anatomy within the group — no synthetic root.
- Item carries `body_id` only; group filtering joins `exam_body`. NO `group_id`
  denormalization on items, NO `methodValue`/`bodyValue` strings anywhere; list
  endpoints resolve display names server-side (joins inside one schema are cheap;
  renames can never strand stale copies).
- Item↔method is the `exam_item_method` join table; item create/update rewrites the
  binding set in the same transaction (delete-by-exam_id + batch insert).

### D3: Zero physical FKs; guards in the service layer

Convention references only (`group_id`/`up_id`/`body_id`/`method_id`/`owner_uid`),
matching `user_info.dep_id` / `org_room.dev_id`. Delete guards: group→bodies,
body→children/items, method→item bindings; each refusal names the blocking resource
so the web page can surface it verbatim.

### D4: Uniqueness — partial unique indexes, service-checked first

`(modality, name)` on `exam_body_group`, `(modality, code)` on `exam_item` and
`exam_method` — all `WHERE deleted = 0` partial unique indexes; the service validates
before write (friendly error) and the index is the race-safe backstop. Soft-deleted
rows free their codes for reuse.

### D5: Modality = dict 1027 values, validated at write time

No parallel vocabulary. REWOUND during apply (the original D5 pointed at V26's 1021,
which the committed V27 superseded with 1027 检查类型 — value = the modality string,
value-set synced with 1024, DR/CT/MR/DSA/US/ENDO complete): the service validates
every write's modality against the 检查类型 catalog resolved BY ITS UNIQUE NAME
(`uk_dict_catal_name` — a plain per-write name-join SELECT; catalog writes are rare,
and a cache would trade guaranteed truth for an invalidation risk). The id flip-flopped
across sessions mid-apply (1021/1027) — the name is the stable anchor, so the service
survives whichever id the platform settles on. NO dict data migration in this change;
the TEST BASE idempotently ensures the value set (DR/CT/MR/DSA/US/ENDO) under the
resolved catalog and heals drifted rows (a parallel seed once wrote sort into enable).

### D6: Migration shape

- `lumen-setting/src/main/resources/db/migration/V1__setting_schema.sql` — six
  CREATE TABLE IF NOT EXISTS + partial unique indexes + seed-free (empty catalog);
  header comment carries the ruling citations; no semicolons inside comments
  (re-execution test discipline).
- `lumen-system/.../V2x__exam_setting_menu.sql` — section 1 menu rows (L2 group +
  4 C pages, CONTENT-guarded serial inserts + setval tail), section 2 F nodes + admin
  auto-grant (V15 discipline), section 3 dict 1021 DR row (ON CONFLICT + setval tail,
  V23/V26 discipline). All idempotent under re-run; asserted by a seed re-execution
  test.
- `docs/deployment/pg-init.sql` — add `setting` schema + `lumen_setting_role` grants
  (own schema USAGE/CREATE + public shared-table SELECT).

### D7: API surface follows OrgDeviceController

Kebab plural resources (`/api/v1/setting/exam-items`…), `GET /page` with a query-bean,
`GET /list` pickers, `GET /{id}`, `POST`, `PUT /{id}` (full update, null-clears
optionals), `DELETE /{id}`. Envelope from GlobalResponseAdvice; errors per
`specs/errors`; `@PreAuthorize`-style perms per the house auth bridge. Item page
resolves 部位/方法 display names server-side so the web table never joins.

### D8: Rep-temp tree semantics

One table, `flag` 0/1: directories and templates are tree nodes; templates are
leaves-in-practice (service does not enforce leaf-only on template rows — RCP parity —
but the UI only offers 新建子级 on any node and templates carry finding/impression).
`GET /tree` returns the full scope tree; `GET /page` lists flag=1 rows under one
`upId` with a name filter. Scope `type` 1/2 with `owner_uid` binding on create
(server-side, ignoring the request body) and owner-only update/delete for type=2.

### D9: Test family

`AuthExamItemApiTests` / `AuthExamBodyApiTests` / `AuthExamMethodApiTests` /
`AuthRepTempApiTests` + `AuthExamSettingPermPinTests` + `AuthExamSettingSeedTests`
(re-execution-as-row-count-no-op). Base-class fixture family registers a new prefix in
the smoke-sweep hard-delete ledger (2026-11-07 ruling). Transactionality of the
item+binding rewrite gets a dedicated failure-injection test (D2).

## Risks / Trade-offs

- Joins on every item page vs denormalized copies: chosen reads-stay-correct over
  write-sync complexity; page volumes are catalog-scale, not event-scale.
- dict 1021 shared mutation (DR row) lives in a lumen-system migration while the
  capability is exam-setting: single-stack touch, recorded in the coordination doc.
- Empty seed catalog means first-run UX needs data entry — accepted for v1 (no
  trustworthy import source; RCP data is platform-scoped).

## Migration Plan

Aggregator → module skeleton → V1 schema migration → system-side menu/dict migration
→ service/mapper/entity → controllers → tests. pg-init.sql updated in the same change
so a fresh deployment gets the schema + role in one pass.

## Open Questions

None — all brainstorm rulings are recorded in the coordination doc.
