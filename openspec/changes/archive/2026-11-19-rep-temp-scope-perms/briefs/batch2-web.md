# Batch 2 Brief: 前端 Segmented 显隐 + 动作键控(tasks 2.1–2.3)

Change: rep-temp-scope-perms · Stack: **仅 lumen-web**

## 必读(按序)
1. `openspec/changes/rep-temp-scope-perms/design.md` — D4(tempPerm 单点键控)
2. `openspec/changes/rep-temp-scope-perms/specs/web-system-console/spec.md` — 9 场景 = 验收标准(最后 3 个是本批新场景)
3. 现状代码:`lumen-web/src/pages/setting/rep-template/index.tsx`(双形态编辑器页:Segmented 公共/个人、目录抽屉、模板抽屉、树上动作、表格动作——注意该页已含分节编辑器,勿动其逻辑)、`lumen-web/src/utils/perm.ts`(usePerm/hasPerm)
4. 测试基线:`lumen-web/src/test/rep-temp-page.test.tsx`(**26 用例全绿 = 回归底线**)

## 关键回归策略(先读懂再动手)
存量 26 用例的权限 fixtures = 基本族四串,而初始 scope 现为公共——新键控下无 public:list 会翻转到个人,**所有存量用例会炸**。正确姿势:
1. beforeEach 权限 fixtures **补 4 串 public 变体**(`setting:temp:public:list/create/update/delete`)——存量用例变成「双族用户」,行为与改前完全一致,26 个保持绿
2. 新增 3 场景用**裁剪权限集**的用例(re-setSession 覆盖)

## 任务

**2.1 键控助手 + Segmented 过滤 + 默认惰性化**:
- `const tempPerm = useCallback((op: 'create'|'update'|'delete') => (scope === TYPE_PUBLIC ? 'setting:temp:public:' : 'setting:temp:') + op, [scope])`
- Segmented options:保持 公共前个人 顺序,公共项仅当 `hasPerm('setting:temp:public:list')`;个人项恒在(页面类门控保证)
- `useState(() => hasPerm('setting:temp:public:list') ? TYPE_PUBLIC : TYPE_PRIVATE)` 惰性初值(无公共权限者不发公共树/表请求)
- TDD:delta 新场景 1(公共 option 隐藏/默认个人/无公共请求)

**2.2 全部动作键控**:树上动作(新建子目录/编辑目录/删除节点)、左按钮(新建目录)、右按钮(添加模板)、表格(编辑/删除)的 hasPerm 全部换 `tempPerm(op)`;TDD:delta 新场景 2(公共 scope 持 list 无 create/update → 读可写隐)+ 场景 3(个人 scope 基本族原样)

**2.3 端到端形态核验**:仅基本族用户:Segmented 单选项、动作全可用(个人 scope)、断言无 type=1 调用(repTempTree 未以 type=1 调)

## TDD 顺序(铁律)
每任务先写失败测试 → RED 确认 → 最小实现 → GREEN;26 存量用例全程绿(fixtures 补强不算弱化——是双族用户语义)。

## 构建/测试
```bash
cd lumen-web && pnpm vitest run src/test/rep-temp-page.test.tsx
cd lumen-web && pnpm exec tsc --noEmit
```

## 提交
pathspec 限定(lumen-web 两文件),风格 `✨ feat(web): ...`。

## 报告
`openspec/sdd/reports/rep-temp-scope-perms-batch2.md`(<60 行)。回报 ≤12 行:Status + 一行摘要 + commit sha + 报告路径。
