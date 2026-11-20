# modality-dict-id-anchor — 模态词表消费者 id 锚定迁移（跨栈）

> Registered: 2026-11-20 · Trigger: the 4 `/setting` pages went fully inert (zero
> API calls) and every backend setting-domain modality path rejects.

## Shared facts (verified 2026-11-19/20 — shared test DB + source grep)

1. The ONLY modality catalog is `dict_catal` id **1024**, name **设备类型** (V27
   unification: 1021 retired, 1027 was a draft flip; `AuthDeviceRoomSeedTests`
   actively guards "「检查类型」 stays absent, 1024 is the single source").
2. Consumers still name-anchored to `'检查类型'` — a catalog that does not exist:
   - Backend single choke point: `DictModalityMapper.countLiveModality`
     (`c.name = '检查类型'` → join always empty → 0) →
     `ExamBodyGroupServiceImpl.requireModality` → 12 call sites across
     ExamItem/ExamMethod/ExamBody/RepTemp ServiceImpls (list AND write paths) →
     every modality-bearing request rejected.
   - Frontend: the 4 setting pages gate ALL loading on
     `dictItemsByName('检查类型')`; the by-name endpoint contract returns `[]`
     for unknown catalogs (never an error) → the empty result is session-cached →
     the `modality` gate never opens → zero page API calls.
3. Working by-name consumers NOT in scope (verified functional today): users page
   (`DICT_EXAM_TYPE = '设备类型'`), room/device pages (设备类型/设备状态),
   roles/logs (grandfathered by ruling 2026-11-19).
4. Spec drift found: main `exam-setting` spec says name-anchored "never a hardcoded
   catalog id" and its scenario still says "dict 1021"; main `system-data`
   "Dict Seed Data" has a by-name 检查类型 scenario. Both stale → the -sev delta
   re-anchors them to id 1024.
5. Why tests stayed green while production broke: no backend test references
   `DictModalityMapper`; web unit tests mock the by-name resolver (mock-level
   green ≠ runtime truth).

## Ruling

dict-lookup-by-id (2026-11-19): the catalog id never moves, the operator may rename
the catalog freely — consumers anchor by ID. `fetchDictItemsById` / `dictItemsById`
infra already exist (landed with number-rule-format-dict).

## Change registry

| Change | Stack | Scope | Status |
|---|---|---|---|
| `modality-dict-id-anchor-sev` | lumen-system (setting) | `DictModalityMapper` SQL anchor `c.id = 1024` + javadoc; spec deltas (exam-setting, system-data); mapper id-anchor test | proposing |
| `modality-dict-id-anchor-web` | lumen-web | `MODALITY_DICT_ID = 1024`; 4 setting pages → `dictItemsById(1024)`; test mock migration; web-system-console delta (id-resolution scenario) | proposing |

## Out of scope (debt — follow-up change)

- Migrating WORKING by-name consumers to the id anchor (room/device/users-page
  other catalogs; roles/audit stay by name per the ruling's grandfather clause).
- rep-template page carries uncommitted rep-temp-normal-range work — the
  id-anchor edit stacks on the same file (coordination note, not a conflict).

## Gate

Verify each stack independently; batch-archive only when BOTH verify.md = PASS
(standard cross-stack gate).
