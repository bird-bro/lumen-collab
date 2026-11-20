# Proposal: us-station-registration-web

## Why

US 工作站是路线 §5.3 的第一个科室工作站，登记页是它的第一张真页面：患者解析/建档 + 登记单全流程，全部对着真实 `/api/v1/us/**` 后端（无 mock 层裁决 2026-11-05）。患者域后端与内核登记域已就绪，本变更补上工作站侧的用户面。

## What Changes

- 新顶级路由域 `us/registration`（US 工作站 W 节点激活后经侧栏进入，workstation↔路由映射静态注册）。
- **患者解析链流**（决策 #2）：身份证/就诊卡号查询 → 精确命中回填 → 软提醒候选列表选人 → 无命中进建档表单（含急诊无名氏占位 PROVISIONAL），全程走 us 侧 resolve 透传端点（`us:order:create` 门控）。
- **`PatientDemographicsForm` 共享组件**（决策 #5）：患者 demographics 字段组，radiology/endo 复用留缝。
- **登记单表单与动作**：检查项目选择（setting 目录 US 模态项）+ 孕检扩展段（妊娠状态/孕周/备注，us-station 扩展）→ 创建预约；列表页（分页/状态标签/accession 列）+ 登记/取消（确认+原因）动作；非法转状态的后端拒绝原文呈现。
- 权限：页面/动作 `us:order:{list,create,update,cancel}` 门控。

## Capabilities

### New Capabilities

- `web-us-station`: US 工作站登记 UI——页面壳、患者解析流、登记单表单与动作、列表。

## Affected Stacks

- single-stack: lumen-web

## Depends-On

- `us-station-registration-sev`（真实 API 先行部署并经用户验证）

## Scope

- In scope: `/us/registration` 页面族 + `PatientDemographicsForm` 共享组件 + us 订单 API client。
- Out of scope: 报告/采集/图文/打印 UI（内核报告域与后续 US change）；HIS 入站工作列表 UI；放射/内镜登记页。

## Success Criteria

- US 工作站激活后可见登记页；解析链四种走向（精确/候选/新建/占位）全部真实闭环。
- 创建预约返回真实 accession；登记/取消动作与后端状态机一致；非法操作呈现后端拒绝原文。

## Constraints

- Real backend only — 每个请求走 vite 代理到真实 us 服务，绝不造数据（2026-11-05 裁决）。
- 复用系统控制台成熟件（表格/抽屉/确认），不新造轮子；US 扩展段仅在本页出现。

## Risks

- setting 目录 API 若无模态过滤参数 → 前端拉全量后按 modality 过滤（目录量小，可接受），不改后端契约。
