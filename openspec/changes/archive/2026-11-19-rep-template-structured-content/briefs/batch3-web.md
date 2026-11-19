# Batch 3 Brief: 前端双形态模板表单(tasks 3.1–3.6)

Change: rep-template-structured-content · Stack: **仅 lumen-web**(跨域禁令:不碰 lumen-sev / openspec/specs)

## 必读(按序)
1. `openspec/changes/rep-template-structured-content/design.md` — D6(双形态编辑器/转换/宽度)、D7(表格摘要渲染)、D8(目录不携带 content)
2. `openspec/changes/rep-template-structured-content/specs/web-system-console/spec.md` — delta 场景 = 验收标准
3. `openspec/changes/rep-template-structured-content/briefs/batch1-backend.md` § 1.3 — content 形状与字段(前端类型须与后端一致)
4. 现状代码:`lumen-web/src/pages/setting/rep-template/index.tsx`(模板表单 = 目录抽屉 + 模板抽屉/Modal 已统一为宽抽屉 640)、`lumen-web/src/api/types.ts`(RepTemp/CreateRepTempBody/UpdateRepTempBody/RepTempNode)、`lumen-web/src/api/rep-temp.ts`
5. 测试基线:`lumen-web/src/test/rep-temp-page.test.tsx`(15 用例全绿 = 回归底线)

## 任务

**3.1 类型契约**(`src/api/types.ts`):`RepTempContent { version: number; sections: RepTempSection[]; impression: string | null }`、`RepTempSection { name: string; finding: string | null; measures: RepTempMeasure[] }`、`RepTempMeasure { code: string; label: string; unit: string | null; normal: string | null }`;`RepTemp`/`CreateRepTempBody`/`UpdateRepTempBody` 加 `content: RepTempContent | null`(请求体允许 null = prose)。

**3.2 双形态表单**(模板抽屉,`index.tsx`):
- 表单顶部「节点类型」区加模式选择:**简单模板 / 分节模板** Radio(仅模板形态;目录形态无表单内容,保持现状)
- 简单模式:现表单不动(名称 + 影像学表现 rows10 + 影像学意见 rows8,640 宽)
- 分节模式:抽屉 `width={structured ? 960 : 640}`;节卡片列表(每卡:节名 Input + 测量项行编辑[项目 code/名称 label/单位 unit/正常范围 normal + 行删 + 「添加测量项」] + 节所见 TextArea + 节折叠/删除)+ 「添加节」按钮;底部全局影像学意见
- 表单状态为纯前端结构(Form state 或受控 state 均可,保存时序列化为 content);节名必填、测量项 code/label 必填(校验信息命名到字段)
- TDD 先行:模式切换渲染、节/测量项增删改序列化、必填校验

**3.3 转换语义**:简单→分节映射(表现→首个节(自动建,节名空待填?不——映射节名用空字符串由用户填?注意:后端 @NotBlank 节名——映射时首节名预填「表现」?按 delta 只规定文字映射;节名让用户填,校验兜底)、意见→content.impression);分节→简单**拒绝**(message.warning 提示会丢节,模式保持分节)。测试覆盖两向。

**3.4 编辑回填**:structured 行(content 非空)直接落分节模式回填节列表;prose 行落简单模式。测试:mock page 返回带 content 的行 → 编辑打开即分节模式且回填。

**3.5 右侧表格摘要**:分节行表现列渲染「N 节 · M 项」(token.colorTextTertiary),意见列取 content.impression(空 —);prose 行渲染不变。TDD 先行。

**3.6 保存链路**:create/update 序列化 content(分节模式);目录形态不携带 content;错误透传 ApiError.msg;成功后 refreshAll 现链路不变。

## TDD 顺序(铁律)
每个任务先写失败测试(vitest + RTL,`vi.mock('@/api/rep-temp')` seam,跟随现有 15 用例 idiom)→ RED 确认 → 最小实现 → GREEN。现有 15 用例必须全程保持绿(回归底线,尤其 prose 全链路)。

## 构建/测试命令
```bash
cd lumen-web && pnpm vitest run src/test/rep-temp-page.test.tsx   # 受影响测试
cd lumen-web && pnpm exec tsc --noEmit                             # 类型检查
```

## 提交
跟随仓库 git log 风格(`✨ feat(web): ...`)。

## 报告
写入 `openspec/sdd/reports/rep-template-structured-content-batch3.md`(<80 行):实现内容、RED/GREEN 证据、改动文件清单、自评、concerns。回报 ≤15 行:Status + 一行摘要 + commit sha + 报告路径。
