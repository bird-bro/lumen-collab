# Design — exam-item-polish-web

## Decisions

- **Banded rows inside the flat single column** (coordination D2): the
  drawer-sections-not-tabs ruling (2026-11-13) bans Tabs / Divider section titles /
  a two-column form GRID — it does not forbid two short controls sharing one row.
  Implementation: antd `Row gutter` wrapping labeled `Form.Item`s in `Col`s, exactly
  three bands (排序+常用项目 / 计费 triple / 得分 pair) after 检查方法; 备注 closes.
- **Labels over placeholders**: every banded field gets a real `Form.Item label`
  (replacing the compact Input.Group's placeholder-as-label); the 得分 pair carries
  `tooltip="留空 = 不计"` preserving the old group-label semantics.
- **备注 column**: plain `ellipsis: true` (antd's native hover title) + `—` for
  null, matching the house tertiary-color pattern used by 项目编码.
- **No component edits**: `console-shell` untouched; all changes stay in the page.
- **Switch semantics unchanged**: `valuePropName="checked"` + `initialValue={false}`
  move with the `Form.Item` into its band; create/update payloads keep
  `isCommon` (entity capability survives — backend change touches the QUERY only).
- **TDD**: page tests first (checkbox absence + query param absence + 备注 column +
  banded labels via `getByLabelText`), then the page/client edits to green.
