<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: org-logo-upload-web
  date: 2026-11-06
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "10/10"
      L3: PASS
---

# 验证结果: org-logo-upload-web

> 生成时间: 2026-11-06 · 总体: **PASS**

## lumen-web

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `pnpm build`(tsc --noEmit + vite)exit 0 |
| L2 规格对齐 | 10/10 已实现 | 0 PARTIAL / 0 NOT_IMPLEMENTED |
| L3 测试 | ✓ PASS | 22 files / 244 tests / 0 failures(增量 31:api-clients 5 + orgs-page 4 + 此前合并单元格 3 等) |

## 场景对齐详情

Deltas: `specs/web-app-shell/spec.md`(MODIFIED Unified API Entry Boundary + ADDED Multipart Upload Transport)· `specs/web-system-console/spec.md`(ADDED Organization Logo Upload Control)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Dev-server request path(`/api/v1` 走统一入口) | ✓ 已实现 | vite.config.ts `/api/v1` 代理(既有条目,行为未动;target 由 `VITE_API_ENTRY_URL` 决定) |
| Static read-back path(`/profile` 同 target) | ✓ 已实现 | vite.config.ts:68-73 新增 `/profile` 条目,同一 `env.VITE_API_ENTRY_URL ?? 'http://localhost:80'` |
| Service host appears in source(违规即违例) | ✓ 已实现 | 全客户端只经 `BASE_PATH`(/api/index.ts:23)拼接,无硬编码服务地址;唯一出口段为规格化的 `/profile` 静态读取 |
| Upload call shape(FormData + Bearer + 无 Content-Type) | ✓ 已实现 | client.ts:187-205 `apiUpload`:FormData 单部件、Bearer 注入、**不设 Content-Type**;测试断言 `headers.get('Content-Type')).toBeNull()` |
| Envelope semantics are shared | ✓ 已实现 | client.ts:106 `unwrapEnvelope` 为两条传输共用(200/204/203/AB 同语义);203 测试断言 `onSessionExpired` 恰好一次 + 抛错;A0400 测试断言 code+msg |
| No invented upload paths | ✓ 已实现 | api/common.ts:16 仅 `/system/common/upload/image`(api spec § Common Image Upload Endpoint 已文档化) |
| Upload round-trip fills the form value | ✓ 已实现 | logo-upload.tsx:64 `onChange(uploaded.path)`(仅成功时);index.tsx:825 `<Form.Item name="logoUrl">` 注册字段——`validateFields` 继续携带 `logoUrl`,提交契约不变;测试:上传→表单值→保存→body 断言 |
| Client-side validation rejects before any request | ✓ 已实现 | logo-upload.tsx:47-58 beforeUpload 白名单 + 2MB → `LIST_IGNORE`(零请求);测试 `.gif` → 警告文案 + `uploadImage` 未调用 |
| Existing logo renders as the tile image | ✓ 已实现 | logo-upload.tsx:83-84 `src={value}` 原样渲染(相对 `/profile/...` 与绝对 `https://...` 通吃);测试:legacy 相对路径回显 |
| Upload failure keeps the dialog usable | ✓ 已实现 | logo-upload.tsx:64-70 失败不 `onChange`(旧值保持)+ `message.error(后端 msg)`;测试:A0400 拒绝 → 旧 src 保留 + 文案上屏 + 抽屉未关 |

## 发现的问题

无阻塞、无 PARTIAL。

设计偏差备注(实现优于原设计稿,TDD 逼出,均已记入 tasks.md 2.2):
1. **受控表单控件**替代 `Form.useFormInstance` watcher:无名 Form.Item 的字段会被 `validateFields` 丢弃(提交契约破坏)且预挂载回填报不到——`<Form.Item name="logoUrl">` 注入 `value/onChange` 才守住规格的"org-save 契约不变"。
2. **去掉 `maxCount=1`**:内部列表封顶会阻断"重选替换";`showUploadList={false}` + `value` 为模型,弹窗 `destroyOnHidden` 每次重挂载。
3. 测试可访问名细节:AntD 两字按钮自动插空格(`保 存`),与既有测试惯例一致。
