# Tasks: us-registration-redesign-api

> TDD house rule: each behavior task goes RED (failing test) → GREEN → refactor. Two stacks
> of code (lumen-us + lumen-system + one additive kernel key) but ONE backend agent; tests
> ride the shared team test server (us/system schemas) with the house fixture contracts
> (unique tokens, @Transactional rollback where the harness allows, hard-delete sweeps
> otherwise). No web surface (proposal ③).

## 1. US V4 vendoring + kernel snapshot widening

- [x] 1.1 RED: US schema guard — after the us ledger runs, `exam_order.booking_time` and
      `exam_slot_config` exist in the us schema with the kernel shapes (extend the existing
      UsSchemaMigrationTests shape or a sibling guard test)
- [x] 1.2 GREEN: `db/migration/V4__kernel_pre_registration.sql` — the kernel V2 statements
      copied VERBATIM (comment lines carry no semicolons; the kernel sources stay untouched)
- [x] 1.3 `PatientSnapshot` gains `cardNo` (additive jsonb key, Lombok combo untouched);
      kernel snapshot round-trip guard (persist with cardNo → read equal; old-shape row
      reads null) — the ExamItemRef.modality precedent
- [x] 1.4 US `buildSnapshot` sets `cardNo` from the create body (RED: create e2e asserts the
      stored snapshot carries cardNo)

## 2. Create contract derives entry status (拍板 02-B)

- [x] 2.1 RED: create with NO bookingTime persists REGISTERED (提交即登记) — the legacy
      FIXED-BOOKED path retires; create result carries the derived status + echoed
      bookingTime (null for walk-ins)
- [x] 2.2 RED: create with a future bookingTime (modality with a live config, below
      capacity) persists BOOKED with the slot anchor — the composed flow in ONE
      TransactionTemplate (port resolve outside, dance + registerOrder inside; the D7 lock
      inherited from the kernel validate)
- [x] 2.3 RED: the four kernel refusals surface VERBATIM through the US create — past
      (预登记时间不能早于当前时间), unconfigured (该检查类型未开通预登记), outside windows
      (预登记时间不在开台时间窗内), at capacity (该时段已满) — nothing persists on any refusal
- [x] 2.4 GREEN: `RegistrationCreateRequest.bookingTime` + `RegistrationCreateResult.
      bookingTime` + `RegistrationOrderView.bookingTime` + the service composition (D1)
- [x] 2.5 GREEN: view/list layering — the page rows carry bookingTime; the 登记 action on a
      REGISTERED order A0400s verbatim (the transitional-break pin)

## 3. Workbench query surface (拍板 03-B)

- [x] 3.1 RED: status multi-select (IN semantics, empty = all) composing with the existing
      conditions
- [x] 3.2 RED: snapshot conditions — patient-id exact (patId), patient-source exact,
      card-no exact (the 1.3 widening feeds it)
- [x] 3.3 RED: exam-item name fuzzy — per-element `jsonb_array_elements_text` LIKE (a
      fragment inside itemName matches; a fragment that only exists in another jsonb key
      does NOT)
- [x] 3.4 RED: apply-time range bounds create_time
- [x] 3.5 RED: `GET /orders/status-counts` — counts per status under the filters minus
      status, zero-filled across all six states
- [x] 3.6 GREEN: `RegistrationOrderPageQuery`/view widening + the count endpoint (D4/D5)

## 4. Slot config administration (拍板 06-A)

- [x] 4.1 RED: CRUD service — create stores one trimmed live row per modality; duplicate
      live modality → A0400; update edits policy fields with the token immutable; delete
      soft-deletes; list returns windows decoded as structured pairs
- [x] 4.2 RED: `SlotConfigController` over `/api/v1/us/slot-configs` — the
      `setting:slot:*` perms family gates every action (perm-enforcement test family)
- [x] 4.3 GREEN: controller/service/dto + the kernel `ExamSlotConfigMapper` reuse (D2)

## 5. System V50/V51 — search filters + 预约设置 menu (拍板 07-A + 06-A)

- [x] 5.1 RED: V50 lands `search_filter` in the system schema (uid/page_key/name/query
      jsonb/default flag + audit columns); the partial unique default guard (one default
      per uid+page_key among live rows)
- [x] 5.2 RED: search-filter REST — list by pageKey (uid-scoped, ordered), create, update,
      delete, set-default full-replace (previous default cleared in the same transaction);
      cross-user row invisible; authenticated-only (no perm family)
- [x] 5.3 RED: V51 seeds the 预约设置 C leaf (path exam-slot, perms setting:slot:list)
      under 检查配置 + the `slot` F buttons (V15 CONTENT-guard discipline, admin
      auto-granted); re-execution no-op; the four existing leaves untouched
- [x] 5.4 GREEN: migrations + the `org.birdbro.lumen.search` domain (controller/service/
      impl/mapper/entity/dto per the hosted-domain shape) + seed tests

## 6. Verification

- [x] 6.1 Full reactor test run from `lumen-sev/` (JDK 11, `./mvnw test -q`) — exit 0,
      zero failures; environmental incidents follow the ① isolate-and-record protocol
- [x] 6.2 `/opsx:verify` three-layer credential — verify.md overall = PASS for the
      coordination gate
- [ ] 6.3 Coordination doc registry updated (us-registration-redesign-api → verified);
      proposal ③ may start (backend-first dependency satisfied)
