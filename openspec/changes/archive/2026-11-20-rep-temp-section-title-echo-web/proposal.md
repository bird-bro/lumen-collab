# rep-temp-section-title-echo-web — 分节卡片标题回显节名（原型拍板点 1 兑现）

**Date**: 2026-11-20 · **Status**: implemented-pending-archive · **Stack**: lumen-web（单栈轻量提案）

## Why

用户 2026-11-20 确认原型 `docs/prototypes/rep-template-report-preview.html`
（拍板点 1）：「节 1 / 节 2」位置编号用户看不懂——卡片标题改为实时回显用户填的
节名，未填显示「节名未填」，右侧保留灰色小号 节 N 编号便于口头沟通；节名输入框
占位同步加示例。其余三拍板（B 测值表式起步 / 节术语保留 / 排序先不加）已录会话
记忆（rep-template-report-render-decision），非本 change 范围。

## What Changes

分节模板抽屉（`/setting/rep-templates`），仅卡片标题与占位文案：

- `TemplateSectionCard` 卡片 title：`节 ${index+1}` →
  `{section.name.trim() || 节名未填(tertiary 灰)}` + 灰色 `节 N` 尾标
  （token 取色，无 hex）；aria-label `节卡片 N` 不动（页测与读屏锚点稳定）；
- 节名 Input placeholder：`节名` → `节名（如：心腔、肝脏）`；
- 节名必填保存拦截不变（未填卡仍被 saveTemplate 拦下）。

**spec delta**：`web-system-console` § Report Template Page（MODIFIED，全 12 场景
超集）——节卡句改写 + 新场景「Card title echoes the typed 节名」。

## Scope

- `lumen-web`: `src/pages/setting/rep-template/index.tsx` +
  `src/test/rep-temp-page.test.tsx`
- specs delta: `web-system-console` § Report Template Page

## Impact

- 无 API 契约变更、无库变更、无后端文件、无 coordination doc（单栈轻量）。
- 归档无顺序依赖。
