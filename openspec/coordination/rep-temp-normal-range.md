# Coordination: rep-temp-normal-range

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "rep-temp-normal-range"`.

## Why

测量项「正常范围」是自由文本（archived `rep-template-structured-content` D2：
`measures[].normal: string|null`），报告填写侧无法用测值比对自动提示异常。
用户裁决 2026-11-19（brainstorm 三拍板）：现在就结构化——`normal` 升级为
区间/文本 tagged union（content version 2），存量 v1 行一次性 DB 迁移，
min/max 独立可空（单边开区间），报告填写侧的异常提示消费留待未来提案。

## Shared Design

Single source of truth for both stacks.

### v2 normal union（两侧同形）

`measures[].normal`：`null | { "kind": "range", "min": number|null, "max": number|null } | { "kind": "text", "text": string }`

- **range**：min/max 独立可空的小数（≥x、≤x 单边开区间直接表达）；双填时
  min ≤ max 否则拒；两端全空拒；无算子枚举（`<5` 与 `≤5` 在模型上同语义
  `max=5`，平坦化已记录，用户确认）。
- **text**：非空、≤64 字符（定性项：阴性/阳性等）。
- **null**：该测量项无正常范围（字段整体可缺省，等价旧 null）。
- `code/label/unit` 不动；`content.version` 服务端钉 2（沿 pin-1 旧纪律）；
  迁移后服务端只认 v2，v1 形态写请求拒（A0400 命名字段）。

### 一次性迁移（V47，lumen-system 账本）

- 幂等守卫：只碰 `content->>'version' = '1'` 的行（重放跳过 v2）；
  新装机（V39 空列）自然 no-op；jsonb 列 + GIN（V39）不动。
- 严格模式集（trim 后）：`19-40`（`-`/`~` 分隔）→ range(min,max)；
  `<5|≤5` → range(null,max)；`>3|≥3` → range(min,null)；其余不可解析
  （含「阴性」）→ text(原文 verbatim)；null → null。确定性映射，零语义丢失。
- 纯 SQL jsonb 重建（jsonb_agg + WITH ORDINALITY 保序），正则解析同规则。

### Stack split / ordering

- **sev 先行**（契约 + 迁移落地）；web 对真后端开发（无 mock，2026-11-05 裁决），
  在 sev verify PASS 后实施。
- spec delta 落点：exam-setting（shape/校验/迁移）、web-system-console（编辑器
  场景，MODIFIED 块按主规格场景超集纪律携带全部 5 个既有场景）。

## Change Registry

| Change | Stack | Scope | Status |
|:--|:--|:--|:--|
| `rep-temp-normal-range-sev` | lumen-sev（lumen-system setting 域） | V47 迁移 + DTO v2 + 校验 | **archived 2026-11-19**（verify PASS 8/8） |
| `rep-temp-normal-range-web` | lumen-web | 编辑器 union 行 + 序列化 v2 | **archived 2026-11-19**（verify PASS 10/10） |

## Decisions Log

- 2026-11-19 用户三拍板：tagged union（否平行字段）；一次性 DB 迁移
  （否双版本读兼容、否自动解析——迁移规则确定性，不可解析降 text 不算猜）；
  min/max 独立可空 + min≤max（否闭区间必填、否算子枚举）。
- 2026-11-19 设计确认（①模型 ②迁移 ③web 编辑器 ④双 change 结构）整体通过。
- 2026-11-19 批量归档（双 verify PASS：sev 8/8 IMPLEMENTED + web 10/10）；V47 在共享
  测试库演练期两次删 flyway history 行重放（注释含分号致 checksum 漂移 + lateral 求值
  先于 ON 的 SRF 标量错误），按「幂等数据迁移删行重放」裁决执行，未 repair-and-diverge。
- 2026-11-19 归档 commit 按路径隔离（用户裁决：共享工作区混有并行会话在途改动
  org-device-drop-box / number-rule-format-dict / lumen-patient，git add -A 会卷入）。
