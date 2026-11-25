# Tasks: us-vocab-dict-catalogs (retrospective — workbench side landed f2ba41e, seed lands now)

- [x] 1.1 V52 seed: 「患者来源」1023 + 「妊娠状态」1002 with four details each (value-keyed) — evidence: V52__us_vocab_dict.sql; UsVocabDictSeedTests green
- [x] 1.2 Seed subset tests — evidence: patientSourceCatalogSeeded / pregnancyStatusCatalogSeeded green (also in the reactor 515/0/0 run)
- [x] 2.1 The workbench resolves all four vocabularies via dictItemsById (id-anchored); hard-coded option constants gone — evidence: f2ba41e (src/utils/dict-items.ts + index.tsx + page tests)
- [x] 2.2 The pregnancy cell conditions on the dict female code; the wide-list 患者类型 filter rides 1023 — evidence: page suite green
- [x] 2.3 Dict failure degrades without blocking — evidence: page suite green
- [x] 3.1 L1 web build + web full suite + backend seed tests green — evidence: verify.md
- [x] 3.2 openspec validate passes — evidence: the synced ADDED requirement (Registration Workbench Dict-Resolved Vocabularies)
