# org-logo-upload 提案说明（跨栈）

> ⚠️ 本文为非权威中文导读，帮助快速理解提案背景与下一步动作；英文 OpenSpec 工件
> （`openspec/changes/org-logo-upload-{api,web}/`）是唯一权威来源。

## 这个提案解决什么

机构管理对话框里的 Logo 字段现在是一个「Logo 地址」文本框（占位符 `https://...`）——
让管理员粘贴一个根本无处可产的 URL。用户裁决（2026-11-06）：**logo 应该是上传图片，
不是 url**，实现参照 OPS 参考项目的图片上传模式 1:1。

## OPS 参照（为什么这样设计）

OPS 的图片上传是两层解耦的：

1. **传输层**：`POST /api/common/upload/image`（`CommonController`）——存文件、返回
   `{path}`，不写任何表；磁盘布局 `upload.home` 根目录 + 子目录 + `yyyy/MM/dd/uuid.ext`
   （`common/FileUtils.uploadFile`，lumen 已有 1:1 副本）；读回走匿名 `GET /profile/**`
   （框架 `SecurityConfig` 已放行）。
2. **记账层**：`sys_attach` 通用附件表（`biz_type` + `ref_id` + `tag`），供真正
   「附件集合」场景的业务页面选择性记账。

logo 属于 1:1 列值场景（存 `org_info.logo_url`，V16 已有列）——OPS 最接近的同类场景
（胶片首页图片）同样不写 sys_attach。**经用户确认：v1 只做传输层**；sys_attach 方向
已记入协调文档（`openspec/coordination/org-logo-upload.md` D2），等第一个真实附件
场景（报告附件/检查照片）触发独立提案再建。

## 已确认的决策（2026-11-06）

| 决策 | 结论 |
|:--|:--|
| 接口归属 | lumen-system（无 launch 模块；控制台/JWT 签发方、机构域 owner） |
| 服务端权限 | 仅登录即可（Spring Security 默认兜底，不加 @PreAuthorize；表单入口已按 `system:org:update` 前端门控） |
| 校验 | png/jpg/jpeg/webp、≤2MB，**前后端双重**校验，拒绝码 `A0400` |
| 附件层 | v1 不建 sys_attach（传输层复用即可，记账层后置） |

## 文件与角色

**后端 `org-logo-upload-api`（lumen-sev）**
- `lumen-system/.../controller/CommonController.java`（新增）——上传端点
  `POST /api/v1/system/common/upload/image`，校验 + `FileUtils.uploadFile` 落盘 +
  返回 `{path}`（经统一 envelope 包裹）
- `lumen-system/src/main/resources/application.yml`——`upload.home` / `upload.avatar`
  提交态安全默认值（无凭据）
- `framework/.../config/WebMvcConfig.java`——**框架适应项 #7**：补全 OPS 被截断的
  `/profile/**` 注册行（加 `file:${upload.home}/` 定位，空默认 = 其他服务行为不变）
- 无数据库迁移（logo_url 列已存在）

**前端 `org-logo-upload-web`（lumen-web）**
- `src/api/client.ts`——新增 `apiUpload()` multipart 传输（不改动 `apiRequest`，
  never-modify 规则；解包逻辑抽私有共享）
- `src/api/common.ts`（新增）+ `src/api/types.ts`——`uploadImage(file) → {path}`
- `src/pages/system/orgs/logo-upload.tsx`（新增）——`LogoUploadField` 上传控件
  （参照 `RegionSelects` 的表单绑定模式），替换抽屉里的 URL 输入框
- `vite.config.ts`——`/profile` 代理条目（与 `/api/v1` 同 target，直连服务开发可回显）

## 关键设计取舍

- **`/profile/**` 在 `/api/v1` 之外**：这是唯一被认可的静态段（api spec 的网关路径
  约定已增补）；vite 代理本次实现，生产 nginx 转发规则随未来的 `docker/` 变更落地。
- **存库值 = `/profile/avatar/2026/11/06/uuid.png`**：既是表单值也是可直接用的
  `<img src>`；历史绝对 http 值照常显示。
- **double-sided 校验**：eis 参考组件只挡前端；本提案前后端都挡（用户选择加固）。

## 下一步动作建议

1. 后端先行：`/opsx:apply org-logo-upload-api`（TDD：先写 `OrgLogoUploadTests` 红，
   再实现控制器 + 补 WebMvcConfig 定位行），跑通 `./mvnw -pl lumen-system -am test`。
2. 后端用户验证后：`/opsx:apply org-logo-upload-web`（对真实后端联调，无 mock 层）。
3. 门禁：两个 change 都 `/opsx:verify` PASS 后一起归档（特征门禁见协调文档）。
4. 注意：`workstation-context` 正占用执行槽位；本特征排队等槽，apply 时按 LoopForge
   流程做槽位交接。
