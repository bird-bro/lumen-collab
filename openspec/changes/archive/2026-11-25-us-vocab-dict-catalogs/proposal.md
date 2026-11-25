# Proposal: us-vocab-dict-catalogs

## Why
The US registration workbench hard-codes its four vocabularies in the frontend
(性别 0/1/2, 证件类型, 患者来源, 妊娠状态 as `const` option arrays). Two of them
have live dict catalogs (性别 1004, 证件类型 1006 - V6 seed) that the page ignores,
and the other two have no catalog anywhere, so the vocabulary lives outside the
dict system the platform uses for every other selector. The user ruled
(2026-11-24) that all four resolve from the dict catalogs, assigning the two
missing ids: 患者来源 = 1023, 妊娠状态 = 1002.

## What Changes
- Backend: one Flyway seed (V52) creates the 「患者来源」 catalog 1023 and the
  「妊娠状态」 catalog 1002 in `public.dict_catal` with their four details each
  (门诊/住院/急诊/体检 and 未孕/早孕/中晚孕/未知), value-keyed like the modality
  catalogs. 1023 reuses the dissolved 诊室类型 band slot V27 retired (never
  seeded - the retirement note is superseded by this ruling); 1002 reuses the
  mislabeled rcp ORG_TYPE slot V6 excluded (also never seeded).
- Frontend: the registration workbench resolves all four vocabularies through
  the session-cached `dictItemsById` (id-anchored per the dict-lookup-by-id
  ruling 2026-11-19): gender options/labels and the 妊娠 cell condition use the
  dict codes (GENDER_FEMALE_CODE = 100402), idType / patientSource /
  pregnancyStatus selects are value-keyed (the stored token stays the dict text
  the string columns already hold), the wide-list 患者类型 filter rides the same
  患者来源 catalog. A failed dict fetch degrades to empty options + one error
  message, never blocking the workbench.

## Affected Stacks
- primary: lumen-web
- secondary (seed + seed test only): lumen-sev

## Depends-On
- none - standalone

## Scope
- In scope: `lumen-sev/lumen-system` V52 seed + `PatientVocabDictSeedTests`;
  `lumen-web` `src/utils/dict-items.ts` (four id exports), `src/pages/us/registration/index.tsx`
  (options/labels/condition), `src/test/us-registration-page.test.tsx` (dict stub + assertions).
- Out of scope: dict management UI, us-station backend behavior (columns keep
  storing the same strings/int codes), other pages' vocabularies, the legacy
  by-name consumers (users/audit migrate separately).

## Success Criteria
- `dict_catal` carries 1023 患者来源 and 1002 妊娠状态 with 4 enabled details each.
- The workbench renders gender radios / idType / patientSource / pregnancyStatus
  from the dict catalogs; the hard-coded option constants are gone.
- The pregnancy cell appears only for the dict female code (100402).
- US suites green; the seed test asserts both catalogs subset-style.

## Constraints
- Ids are user-assigned anchors (2026-11-24): 1023 / 1002 never move.
- ON CONFLICT (id) DO NOTHING + setval tails per the V44 seed shape; no history
  rewrites.
- Stored tokens unchanged: patientSource / pregnancyStatus keep dict-value text,
  gender keeps the dict numeric code (data-spec ruling).

## Risks
- Legacy dev rows carry gender 0/1/2 → pre-seed archives resolve with a blank
  gender radio / raw-code label; dev data re-registers, no compat mapping.
- 字典里的证件值只有 居民身份证/护照 → the drawer offers fewer idTypes than the
  old constant; operator extends via 字典管理 (that is the feature, not drift).
