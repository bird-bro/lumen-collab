# Design

## Context

The patient domain decisions were ruled in the 2026-11-04 explore session (recorded in
memory + `lumen-reference/NOTES-us-workstation.md`); this change lands decisions #1-#5.
The EIS reference supplied the concrete failure shapes this design must prevent: patient
reuse trusting a client uid (`OrderInfoServiceImpl.commitUsOrder`), no uniqueness on
`card_no` (`getByCardNo ... ORDER BY ins_time DESC LIMIT 1`), patient data in three
tables, zero merge tracing. Stack constraints that shape the design: no MQ is deployed
(docker compose is a deferred change; RocketMQ is version-managed in `lumen-bom` only),
Redis is an optional dependency (`lumen.redis.enabled`), every service owns its schema
and Flyway ledger, and menu seeds are written cross-schema into `system.menu_info`
(V22 workstation-node precedent).

User rulings during this proposal (2026-11-16): event carriage = outbox table, no MQ;
number-rule management = with a console page; patient UI beyond that page = excluded;
rule table stays in the patient schema (the page mounts under 设置).

## Goals / Non-Goals

**Goals:**

- One patient master archive serviceable by every future department workstation via
  `/api/v1/patient/**` and (later) Feign.
- Identity resolution that makes duplicate archives structurally impossible for
  id_no-bearing patients while staying usable for unnamed emergencies.
- A merge contract the future `lumen-core` can consume without patient-domain knowing
  exam orders exist.

**Non-Goals:**

- No MQ producer/consumer (outbox rows stay PENDING; the broker lands with its own
  change when `lumen-core` needs the events).
- No accession (exam number) issuance - that is the exam side's number (decision #1).
- No HIS patientId auto-mapping logic (identifier rows carry the mapping; the
  integration change consumes it).
- No full manual archive merge (two formal archives) - v2.
- No patient UI beyond the number-rule page (resolution soft-prompt, backfill merge,
  archive search live in future workstation changes).

## Decisions

### D1: Issuance = DB row lock, no Redis

Number issuance takes `SELECT ... FOR UPDATE` on the rule row, increments
`serial_cur`, and writes the number inside the caller's transaction - with the counter
increment committing in its OWN inner transaction (`REQUIRES_NEW`, wording amendment
2026-11-17, b2 review): a failed archive creation then burns the serial instead of
rolling it back for reissue - the "numbers are never reused" line this same decision
carries. Alternatives:
Redis INCR (turns an optional dependency mandatory; counter rebuild after loss) and PG
native sequences (fastest, but cannot day-reset, and stamp rules need per-day reset).
EIS used a Redis spin-lock because its counter lived on a MySQL row; a single PG row
lock at archive-creation frequency needs no external lock. Gaps from burned numbers
(failed creation after issuance) are tolerated - image numbers must never be reused.

### D2: Event carriage = outbox table, contract-first

`patient_event_outbox` (event_type, patient_id, payload jsonb, status PENDING,
append-only). The consumers are future: `lumen-core` snapshot refresh (PATIENT_CHANGED)
and exam-order repoint (PATIENT_MERGED - the repoint executes in the department
service's own schema; patient-domain never writes another service's tables). Until a
poller/consumer change lands, rows accumulate in PENDING - the contract exists as real,
testable code rather than a paper spec. Alternatives rejected: direct RocketMQ publish
(pulls the broker + compose change into this proposal) and doc-only contract
(unvalidated until first consumer arrives).

### D3: Rule table in the patient schema, page under 设置

`patient_number_rule` carries config parameters plus the hot `serial_cur` counter - it
is runtime state written on the archive-creation path, not a read-only catalog like
`exam_item`. Keeping it in the patient schema keeps the issuance transaction local and
avoids making `lumen-system` availability a hard runtime dependency of registration
(logged-in sessions survive system restarts; registration must too). The console page
still mounts under the 设置 L1 (new 「患者配置」 group) - page organization and table
ownership are independent. The `rule_type` column generalizes: the future accession
rules follow the same per-service pattern on the exam side (EIS precedent:
per-modality rule rows).

### D4: Uniqueness = partial index on live rows

`(id_type, id_no)` partial unique index `WHERE id_no IS NOT NULL AND merged_into IS
NULL` - merged rows drop out of the key so a re-registered patient with the same id_no
is not blocked by a merged predecessor. Pinyin is generated server-side (EIS did the
same); gender/birth stay typed columns, not EIS's string-birthday.

### D5: Cross-schema menu seeds follow the V22/V32 discipline

Menu nodes (L2 「患者配置」 group + 号码规则 page + F buttons) seed into
`system.menu_info`/`role_menu` through the patient module's own Flyway ledger,
CONTENT-guarded serial inserts keyed by perms, admin auto-grant, non-admin ancestor
chain completion, re-execution no-op - the same discipline as V22 (workstation W
nodes) and V28/V32 (setting groups). Exact L2 icon glyph is picked from the existing
`ops-icons` family during apply (task will pin it); the spec requires only a
family-resident glyph.

### D6: Module shape mirrors lumen-system conventions

`lumen-sev/lumen-patient`, package `org.birdbro.lumen.patient`, app class
`PatientApplication` at the service root, packages `controller / service / mapper /
entity / dto` with business services as interface + `impl/` (naming.md ruling),
port 8082 (coordination port table row added), `spring.application.name=lumen-patient`,
nginx `/api/v1/patient/**`. Data-layer tests run against the team test-server PG per
the existing lumen-system pattern.

## Risks / Trade-offs

- [Outbox rows accumulate with no consumer] -> Append-only table with status column;
  the first consumer change adds the poller; volume is archive-correction scale, not
  event-storm scale. Verify later adds a growth assertion only if needed.
- [Issuance row lock serializes archive creation] -> Single-row lock at registration
  desks' rate is sub-millisecond; the day-reset branch is the only read-modify-write
  nuance and is covered by the concurrency test.
- [Cross-service repoint contract unproven until lumen-core] -> The PATIENT_MERGED
  payload names both ids explicitly; the contract is exercised at the API test level
  now and end-to-end when the kernel lands.
- [Number gaps from burned issuance] -> Accepted (medical image numbers are
  gap-tolerant; EIS burned numbers the same way); never reissue a burned number.
- [Pinyin edge cases (polyphonic names)] -> Server-side generation with the existing
  utility family; wrong pinyin only degrades search, never identity (id_no/card_no
  anchor resolution).

## Migration Plan

Flyway V1 in the `patient` schema creates the five tables + partial unique index +
seeds (default `pat_id` rule: `yymmdd` stamp + 4-digit serial). Menu seeds ride the
same ledger (cross-schema `system.menu_info` writes, V22 discipline). No data
migration from any legacy system (greenfield domain). Rollback = drop the module and
schema; no other service depends on it yet.

## Open Questions

None - the mechanism choices (D1/D2/D3) and the menu mount point were ruled with the
user during this proposal.
