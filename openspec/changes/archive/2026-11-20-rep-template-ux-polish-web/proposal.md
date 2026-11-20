# rep-template-ux-polish-web — 报告模板页收尾批（预览弹框/icon 化/就地节名/改名/去项目输入）

**Date**: 2026-11-20 · **Status**: implemented-pending-batch-archive · **Stack**: lumen-web（coordination rep-template-ux-polish）

## Why

用户裁决 2026-11-20 六连指令：① 预览改点击按钮+弹框，撤抽屉底部常驻；② 项目输入
下线（打印不显示，配合 sev code 可选）；③ 节所见行的 插入占位 按钮删除；④ 折叠/
删除节/删除/添加测量项 全部 icon 化；⑤ 节名不单独一行、卡片标题内就地编辑；⑥
「节所见」改「影像所见」、「影像学意见」改「影像意见」。预览增强（双栏/全屏）用户
明示先不做；打印模板页暂缓另立项。

## What Changes

- **预览弹框化**：抽屉 footer 左侧 预览 按钮 → Modal（880 宽，footer=null 关闭），
  常驻底部块删除；弹框内容 = 原预览（段落 2em + amber 占位 + 分节 B mini 表）；
- **卡片 icon 化**：折叠/展开（Up/Down chevron）、删除节/删除测量项（trash, danger）、
  添加测量项（plus dashed icon-only）——全部保 aria-label；
- **节名就地编辑**：卡片标题 = 无边框输入（variant borderless，占位含示例）+ 灰色
  节 N 尾标；原 body 输入行与标题回显渲染退役；保存必填拦截不变；
- **项目输入下线**：测量行 = 名称/单位/正常范围类型/区间|文本/删除；state 保留
  code 透传（存量行回填不丢，full-replace 不冲掉）；序列化 `code: ?.trim() || null`；
  客户端「测量项项目不能为空」校验删除（sev 放行可配对 change）；
- **改名**：节所见 → 影像所见（label/aria/占位），影像学意见 → 影像意见
  （Form label/extra aria/预览标题/右表列题）；
- **节所见行 插入占位 按钮删除**（表单 表现/意见 的 extra 槽按钮保留）。

## Scope

- `lumen-web`: `src/pages/setting/rep-template/index.tsx`、`src/api/types.ts`
  （RepTempMeasure.code → string|null）、`src/test/rep-temp-page.test.tsx`
- specs delta: `web-system-console` MODIFIED ×2（Report Template Page +
  Template Placeholder Convention and Live Preview，均超集）

## Impact

- 无路由/权限变更；content 序列化 code 可空与 sev 配对；
- 页测 42/42 → 全量 448/448；build ✓。
