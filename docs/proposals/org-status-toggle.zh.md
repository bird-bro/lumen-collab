# org-status-toggle 提案说明

> 本文件是面向用户的中文说明，非权威规范；权威内容以 `openspec/` 英文规范为准。

## 提案解释

为了让 `/system/orgs` 的院区状态开关和 `/system/users` 的启用开关保持一致，本提案新增
一个显式的机构状态接口：

```http
PUT /api/v1/system/orgs/{id}/status
```

请求体只包含：

```json
{ "status": 0 }
```

或：

```json
{ "status": 1 }
```

其中 `1` 表示启用，`0` 表示停用。接口继续使用现有的 `system:org:update` 权限，不新增
菜单按钮权限，也不改数据库表结构。

## 文件路径与角色

- `openspec/coordination/org-status-toggle.md`：跨栈协调文档，记录共享设计和变更登记。
- `openspec/changes/org-status-toggle-api/`：后端变更提案与验收场景。
- `openspec/changes/org-status-toggle-web/`：前端变更提案与验收场景。
- `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/system/controller/OrgController.java`：
  新增状态接口入口。
- `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/system/dto/SetOrgStatusRequest.java`：
  新增状态请求体校验。
- `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/system/service/OrgService.java`：
  新增状态更新服务方法声明。
- `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/system/service/impl/OrgServiceImpl.java`：
  实现只更新状态字段的逻辑。
- `lumen-web/src/api/orgs.ts`：新增前端 typed client。
- `lumen-web/src/pages/system/orgs/index.tsx`：让院区列表状态开关调用新接口。
- `lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/system/AuthOrgCrudTests.java`：
  后端集成测试。
- `lumen-web/src/test/orgs-page.test.tsx`：前端页面交互与权限测试。

## 下一步行动建议

1. 先合入并部署后端变更，确认 `/system/orgs/{id}/status` 可用。
2. 再让前端状态开关切到新接口，避免前端先调用导致 404。
3. 后端执行 `./mvnw test -q`，前端执行 `pnpm test` 和 `pnpm build`。
4. 两个 OpenSpec change 都验证通过后，再一起执行归档流程。

## 流程计划

1. 后端：新增请求 DTO 和服务方法，控制器暴露 `PUT /system/orgs/{id}/status`。
2. 后端测试：验证成功切换和非法状态拒绝。
3. 前端：新增 API client，并把列表 Switch 从 `updateOrg` 切到 `setOrgStatus`。
4. 前端测试：验证请求路径、请求体、刷新行为和权限禁用。
5. 验证：两侧构建和测试通过，OpenSpec gate 通过后一起归档。
