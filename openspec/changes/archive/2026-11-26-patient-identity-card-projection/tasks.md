# Tasks

## 1. Identity card projection (TDD)

- [x] 1.1 Internal API test FIRST: the resolved identity carries the archive's latest
      card identifier as `cardNo` (a resolve-by-cardNo match echoes the searched card;
      a resolve-created archive echoes its persisted card; an archive with no card
      identifier projects null) — red
- [x] 1.2 Add `cardNo` to `PatientIdentityResponse` + the `toIdentity` projection
      (the latest live `id_type='card'` identifier row via `PatientIdentifierMapper`) — green

## 2. Change-scoped regression

- [x] 2.1 Backend suite green — verify: `./mvnw test -q` exit 0 (JAVA_HOME pinned)
