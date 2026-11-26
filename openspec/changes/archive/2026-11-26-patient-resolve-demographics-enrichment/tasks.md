# Tasks

## 1. The composite match enrichment (TDD)

- [x] 1.1 Archive service test FIRST: a resolve-or-register over a sparse archive
      (card-matched) with the request carrying id_no/birth_date/phone fills ONLY the
      null master fields + appends a NEW card identifier; a second identical call is
      idempotent (no duplicate card rows, no rewrites); an archive with existing
      values never overwrites — red
- [x] 1.2 Implement the minimal-merge enrichment in the composite's matched branch
      (the null-fill + the card append via the existing insertIdentifier; the lookup
      path untouched) — green

## 2. Change-scoped regression

- [x] 2.1 Backend suite green — verify: `./mvnw test -q` exit 0 (JAVA_HOME pinned)
