# Design: number-rule-format-dict-web

- Load 「编号格式」 (catalog 1029) through the id-anchored `dictItemsById` resolver (2026-11-19 id-anchor ruling).
- Bind the format Select to `stamp`; require it only when the stamp switch is on.
- Keep dictionary failure non-blocking for the page by degrading to an empty Select.
- Align drawer chrome and compact the prefix/suffix fields.
