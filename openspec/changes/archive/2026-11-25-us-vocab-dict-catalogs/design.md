# Design: us-vocab-dict-catalogs (retrospective closure)

> Written retrospectively at archive time — the workbench side landed with
> f2ba41e; the V52 seed lands with this closure. User ruling 2026-11-24: all four
> workbench vocabularies resolve from dict catalogs (患者来源 = 1023, 妊娠状态 =
> 1002; 性别 1004 / 证件类型 1006 already existed).

## Decisions

- **D1 — V52 seeds the two missing catalogs** in `public.dict_catal` (value-keyed
  like the modality catalogs): 1023 reuses the dissolved 诊室类型 band slot V27
  retired, 1002 reuses the mislabeled rcp ORG_TYPE slot V6 excluded — both never
  seeded before.
- **D2 — id-anchored consumption** (the dict-lookup-by-id ruling 2026-11-19):
  session-cached dictItemsById; the stored tokens stay the dict text the string
  columns already hold (no data migration); gender rides the dict codes
  (GENDER_FEMALE_CODE = 100402).
- **D3 — failure degrades**: a failed dict fetch renders empty options + one error
  message; no hard-coded fallback set reappears.

## Open Questions

(none)
