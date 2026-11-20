# Coordination: rep-template-ux-polish

## Decision

- 用户裁决 2026-11-20（报告模板页收尾批）：预览改按钮+弹框（撤抽屉底部常驻）；
  卡片按钮全 icon 化（折叠/删除节/删除测量项/添加测量项）；节名进卡片标题就地
  编辑（无边框输入+灰色 节 N 尾标，标题回显方案退役）；「节所见」改「影像所见」、
  「影像学意见」改「影像意见」；节所见行的 插入占位 按钮删除。
- **测量项 项目(code) 输入下线**（用户问询「需要的吗」→ 打印版面 B 表不显示项目码、
  医生自造码纯负担 → UI 去除）：sev 把 `RepTempMeasure.code` 的 `@NotBlank` 放行为
  可选（字段保留——存量行 code 不丢、API 直写仍可带、未来报告域统计锚备用）；
  编辑回填隐式保留存量 code（无输入框但 state 透传，full-replace 不冲掉）。
- 打印模板页 **暂缓**（用户：先不急）——报告模板内容侧至此收尾。
- 定界符维持 【左|右】（用户纠结 {} 后经 IME 顺向论证复核维持）。

## Change registry

| Stack | Change |
|:--|:--|
| sev (lumen-system setting) | `rep-template-ux-polish-sev`（code 可选） |
| web (rep-template page) | `rep-template-ux-polish-web`（预览弹框/icon 化/就地节名/改名/去项目输入） |

Gate: 双栈各自 verify PASS 后批量归档。
