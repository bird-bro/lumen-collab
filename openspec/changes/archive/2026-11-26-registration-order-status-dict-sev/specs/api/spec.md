# api Delta

## MODIFIED Requirements

### Requirement: Dict Items Lookup By Catalog Name

<!-- synced-path correction: audit-log-unify-api 2026-11-10 — the role-type-dict
     archive recorded `/dicts/items/{catalogName}` but the wired endpoint (commit
     925c852), the frontend client, and that change's own archived verify all use
     `/dicts/detail/items/{catalogName}`; the spec now matches the implementation -->
`lumen-system` SHALL expose a read-only lookup `GET /api/v1/system/dicts/detail/items/{catalogName}`
that resolves one dict catalog by its unique `name` and returns its live, enabled
details as a slim `{code, value}[]` (the V9/V12 single-text model) in the standard
envelope, ordered by `sort` ascending, plus an OPTIONAL `tag` display-metadata key
(the V9 spare pair — the registration-order-status-dict color carrier, user ruling
2026-11-26): items whose detail row carries a `tag` serialize the third key, items
without one keep the strict two-key `{code, value}` shape (`@JsonInclude(NON_NULL)`
— existing consumers unaffected). The twin `items-by-id/{catalId}` lookup (the
dict-lookup-by-id ruling, id-anchored) carries the same shape. The endpoint is for
label-resolution consumers such as the role console and is
authenticated-only (no permission string — see auth-rbac).

#### Scenario: Items returned for a known catalog

- **WHEN** an authenticated client requests `GET /api/v1/system/dicts/detail/items/角色类型`
- **THEN** the envelope carries `code: "200"` with the catalog's live enabled
  details as `{code, value}[]` in `data`, ordered by `sort` ascending

#### Scenario: Unknown catalog resolves to an empty list

- **WHEN** an authenticated client requests `GET /api/v1/system/dicts/detail/items/不存在目录`
- **THEN** the envelope carries `code: "200"` with an empty `data` array

#### Scenario: Disabled details are excluded

- **WHEN** a detail row of the catalog carries `enable = 0`
- **THEN** it is absent from the response

#### Scenario: Lookup requires authentication but no permission string

- **WHEN** an authenticated user WITHOUT `system:dict:list` calls the endpoint
- **THEN** the request succeeds (`code: "200"`) — the dict page gate does not apply
- **AND** an unauthenticated call is rejected like every other endpoint

#### Scenario: Tagged items carry the optional display metadata

- **WHEN** a detail row carries a `tag` value (e.g. the 1031 登记单状态 color token)
- **THEN** the item serializes the third `tag` key alongside `code` and `value`
- **AND** a detail row without a `tag` keeps the strict two-key `{code, value}` shape
  (the key never serializes as an explicit null)
