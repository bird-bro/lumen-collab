# lumen-collab 项目指南（人类阅读）

> ⚠️ 非权威文档，仅供人类阅读。
> AI 自动加载的文件（根/各栈 `CLAUDE.md`、`AGENTS.md`、`.claude/rules/*.md`）均为英文且为真相源；
> 本文件与它们不一致时，以英文文件为准。本文件位于 `docs/`，不在任何自动加载路径中（Claude/Codex 不加载 docs/）。

## 这是什么
本项目用 Loop Engineering 框架协作开发：OpenSpec 定方向（WHAT）、Superpowers 强纪律（HOW）、Harness 编协作（WHO）。
每个功能走闭环：propose → apply → verify → archive。

## 去哪找什么
| 你想找的 | 位置 | 语言 |
|:--|:--|:--|
| 项目业务/架构/模块图 | `openspec/project.md` | 英文 |
| API/数据/错误契约 | `openspec/specs/` | 英文 |
| 各技术栈编码规范 | `<stack>/CLAUDE.md` | 英文 |
| 命名等通用规则 | `.claude/rules/*.md` | 英文 |
| 斜杠命令用法 | `.claude/commands/opsx/` | 英文 |
| 验证 skill/命令 | Claude: `.claude/commands/opsx/verify.md`; Codex: `.codex/skills/openspec-verify/` | 英文 |
| 构建测试配置（验证用） | `openspec/verify.config.yaml` | 英文 |
| 提案中文介绍 | `docs/proposals/<name>.zh.md` | 中文 |
| 本人类导览 | `docs/GUIDE.zh.md`（本文件） | 中文 |

## 日常工作流（中文口语版）
1. 想做新功能 → 说"我要加 xxx 功能"或 `/opsx:propose <名字>`，AI 先澄清需求再写 spec
2. 实施 → `/opsx:apply <名字>`，AI 按 TDD 逐任务实现，每任务后跑构建检查
3. 验证 → `/opsx:verify <名字>`，跑三层（构建/规格对齐/测试），生成 `verify.md`
4. 归档 → `/opsx:archive <名字>`，检查 `verify.md` 门禁后归档

## 跨栈功能协调（前后端联动）
当一个功能跨多个栈（如前端+后端分属独立仓库），单栈 change 不够--前端 agent 按跨域禁令正确排除后端，但"声明的后端依赖"没有归属会被静默丢失（没人创建对应的兄弟 change）。OpenSpec 1.6.0 移除了原生 initiative/workspace，脚手架改用 LoopForge 约定，已为 ≥2 栈自动建好：
- **协调文档**（`openspec/coordination/`）已自动建立 -- 跨栈"父级"（共享设计/决策 + 各栈 change 登记表），CLI 不解析
- **子级 change**：各栈 `openspec new change <名> --goal "<功能>"`，用 `--goal` 软标签关联到功能
- **登记**：把各栈 change 填进 `openspec/coordination/<功能>.md` 的登记表
- **完成门禁**：所有登记的 change 都 verify PASS -> 功能才算完 -> 各自归档
- **协调会话**：`openspec workset open <项目> --tool code`（IDE 打开所有栈；1.6.0 暂时禁用 agent 直接打开，需手动在项目根启动 codex/claude）

## 约定速记
- 先 spec 后代码：没有 spec 不写代码
- 跨域禁止：后端 agent 不写前端代码，反之亦然
- 自动加载文件只写英文：中文说明写在本文件，避免每会话浪费 token
- 验证凭证：`verify.md` 是归档的通行证

## 常用命令
- `scaffold.sh check ./<project>` - 审计项目合规度（含 O7 中文检查）
- `scaffold.sh tokens ./<project>` - 测自动加载文件 token 数与中文占比
- `scaffold.sh list` - 预览脚手架生成哪些文件

> 维护提示：英文 auto-loaded 文件改了，按需把关键点同步到本文件即可，不必逐行对照。
