# Tasks

## Batch 1 — shell + clients

- [ ] 1.1 Route + page shell (`us/registration`), workstation static mapping, perm gates; API
      client `src/api/us-orders.ts` typed from the us contract. Test: shell render + perm hiding.
- [ ] 1.2 `PatientDemographicsForm` shared component (fields from the archive shape + PROVISIONAL
      toggle). Test: field rendering + placeholder path.

## Batch 2 — flows

- [ ] 2.1 Patient resolution flow: lookup bar → exact fill / candidate modal / new-archive path.
      Tests: all four resolution outcomes through the mocked seam.
- [ ] 2.2 Order form (US items filter + pregnancy ext block +急诊) and create submit; success
      surfaces the accession. Test: create payload shape + ext mapping.
- [ ] 2.3 Order list + 登记 / 取消 (reason confirm) actions; verbatim backend refusals; state tags
      + 急诊 marker. Tests: actions, refusal rendering, list rendering.

## Batch 3 — verify

- [ ] 3.1 `pnpm build` + full `pnpm test` green; `/opsx:verify` credential.
