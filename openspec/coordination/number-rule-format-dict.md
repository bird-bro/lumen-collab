# Coordination: number-rule-format-dict

## Decision

- Number-rule stamp formats are operator-maintained in the 「编号格式」 dict catalog (id 1029; seeded 「号码戳格式」, renamed by V45 — consumers anchor by id); each item's `value` is the Java date/time pattern stored verbatim in `number_rule.stamp`.
- The drawer uses the dict options for `stamp`, validates a selection when the stamp switch is on, and degrades to an empty Select if the dict lookup fails.
- The rule drawer follows the system-console drawer conventions (`destroyOnHidden`, form disabled while saving, full-width controls, compact prefix/suffix layout).
- Timestamp patterns are allowed, but the existing issuer resets the serial whenever the generated stamp token changes; therefore a minute/second pattern resets at that granularity.

## Change registry

| Stack | Change |
|:--|:--|
| Backend/system data | `number-rule-format-dict-sev` |
| Frontend | `number-rule-format-dict-web` |

## Deployment order

1. Apply backend V43 (dict seed).
2. Deploy frontend drawer changes.
3. Verify against real APIs.
