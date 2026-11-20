# Design: number-rule-format-dict-sev

- Use catalog 1029 / details 1029001–1029006 with ON CONFLICT-guarded deterministic ids.
- Store each Java pattern in dict `value`; `number_rule.stamp` continues to store that value verbatim.
- Resync dictionary sequences after explicit-id inserts.
- No API or issuance-mechanism change; malformed custom patterns remain guarded by the existing issuance format check.
