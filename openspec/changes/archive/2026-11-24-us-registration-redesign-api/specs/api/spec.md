<!-- us-registration-redesign-api delta. 拍板 07-A (我的查询 backend-persisted).
     Generic, reusable by every workbench list; column settings stay localStorage (frontend-only). -->

## ADDED Requirements

### Requirement: Saved Search Filter Endpoints

The system service SHALL expose generic saved-search persistence over
`/api/v1/system/search-filters` for every workbench list: list by page key, create, update,
delete, and set-default. Rows are USER-OWNED (`uid` scoping — a user never sees or mutates
another user's filters), authenticated-only WITHOUT a permission family (the dict-lookup
authenticated-only precedent — saving a personal search is not an admin surface). The
`query` jsonb passes through verbatim (the per-page filter shape belongs to each workbench);
the `pageKey` discriminates pages; the default flag holds ONE default per (uid, pageKey).
The response envelope and ID serialization follow the house rules.

#### Scenario: List returns the caller's filters for a page

- **WHEN** an authenticated user lists filters for a `pageKey`
- **THEN** only that user's live rows for that page return, ordered by update time descending

#### Scenario: Create stores a named filter

- **WHEN** an authenticated user creates a filter (pageKey, name, query jsonb, optional
  default flag)
- **THEN** the row stores uid-owned with the query jsonb verbatim and returns the row

#### Scenario: Set default replaces the previous default

- **WHEN** an authenticated user sets one of their filters as the page default
- **THEN** the row's default flag turns on and the user's previous default for the SAME
  pageKey turns off (one default per uid + pageKey)

#### Scenario: Update and delete touch only the owner's rows

- **WHEN** an authenticated user updates or deletes a filter they own
- **THEN** the mutation applies
- **WHEN** the same attempt targets another user's filter id
- **THEN** the row is not found for the caller (uid scoping — no cross-user access)

#### Scenario: Unauthenticated access is refused

- **WHEN** an unauthenticated client calls any search-filter endpoint
- **THEN** the authentication layer refuses
