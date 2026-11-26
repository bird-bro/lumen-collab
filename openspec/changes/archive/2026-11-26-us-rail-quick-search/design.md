# Design: us-rail-quick-search

> RECORDING change — the implementation is already committed (`9ca86f4`/`e737153`);
> this file cites where the contract lives, nothing new is built.

## D1 — The keyword condition (backend, committed)

`RegistrationOrderPageQuery.keyword` + the OR-block in `appendWideConditions`
(RegistrationOrderServiceImpl): `(snapshot->>'name' LIKE %kw% OR accession_no LIKE %kw%
OR snapshot->>'patId' LIKE %kw% OR snapshot->>'cardNo' LIKE %kw%)` wrapped in one
`.and(...)` group — AND-composed with the other wide conditions; both the page query and
the status-count meter share the builder (the meter narrows with the search). The v1
影像号 rides the accession family (no independent imaging-number field on the order).

## D2 — The rail-search box (frontend, committed)

`UsOrderPageQuery.keyword` + the page's `railSearch`/`railSearchDebounced` pair (400ms
debounce) feeding BOTH `pageOrders` and `statusCounts`; the input sits on the compact
rail's white rail-chips block (the prototype's `.rail-search` row) — full-width, prefix
search icon, placeholder 姓名 / 检查号 / 影像号. The rail head splits into the
prototype's two blocks (tinted datenav band / white chips block, each with its own
divider); the chips row is a shared variable across both states.

## D3 — Test evidence

- Backend: `UsRegistrationApiTests.railQuickSearchOrMatchesTheIdentityHandles` (name hit,
  accession hit, meter narrowing; archived-name creates per the snapshot-name lesson;
  raw Chinese in the URL — pre-encoded keywords bind undecoded through exchangeJson).
- Frontend: `us-registration-page.test.tsx` rail quick-search test (debounced keyword
  rides both calls) + the chips tests (aria-pressed / check glyph / data-status-hue).
