/**
 * Patient-domain typed clients (patient-domain task 5.1) - the 号码规则 console
 * page's transport. SELF-CONTAINED module (types + client in one file): the
 * house convention keeps shared types in `./types`, but that file is currently
 * carrying a parallel session's uncommitted exam-item work - a patient section
 * there would couple two changes' commits. When the parallel change archives,
 * the types MAY migrate to `./types` for uniformity; the client seam is
 * identical either way (BASE_PATH + apiRequest envelope contract).
 *
 * (number-rule-unification-web: the rule CRUD surface RETIRED from patient - the
 * platform directory lives in the system service, typed by `api/number-rules.ts`.)
 */

