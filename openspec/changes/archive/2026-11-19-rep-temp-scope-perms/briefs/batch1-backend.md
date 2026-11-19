# Batch 1 Brief: 后端 V41 种子 + 服务层族守卫(tasks 1.1–1.4)

Change: rep-temp-scope-perms · Stack: **仅 lumen-sev**(并行会话活跃:50 脏项+暂存改名,**提交必须 pathspec 限定**;不碰 exam-method 系列文件/V38/SettingSchemaSeedTests 除非 1.4 明确最小行)

## 必读(按序)
1. `openspec/changes/rep-temp-scope-perms/design.md` — D1(admit-then-assert)、D2(断言点/顺序/AccessDeniedException)、D3(V41 种子)、D5(测试矩阵)
2. `openspec/changes/rep-temp-scope-perms/specs/exam-setting/spec.md` — 5 场景 = 验收标准
3. `openspec/changes/rep-temp-scope-perms/specs/system-data/spec.md` — 种子场景 = 验收标准
4. 种子先例:`lumen-sev/lumen-system/src/main/resources/db/migration/V28__exam_setting_menu.sql`(F 行 NOT EXISTS 形态 + role 1 守护授权 + setval 尾——照抄结构)
5. 现状代码:`RepTempServiceImpl.java`(requireOwnedRow/create/update/delete/getTree/getPage)、`RepTempController.java`(类门控+四钉)、`framework/.../security/handle/AuthDenyAdvice.java`(A0301 信封路由)
6. 测试习惯:`AuthRepTempPermPinTests.java`(权限钉测试 idiom + authorities 装配方式)、`AuthRepTempApiTests.java`

## 任务

**1.1 迁移** `V41__rep_temp_public_perms.sql`(V40 已占,勿动):
- 4 个 F 节点,up_id = 报告模板 C 行(perms='setting:temp:list'),NOT EXISTS 守卫,sort 4–7:
  公共模板可见/`setting:temp:public:list`;新建公共模板/`...:create`;编辑公共模板/`...:update`;删除公共模板/`...:delete`
- role 1 守护授权(照抄 V28 的 role_menu INSERT ... WHERE NOT EXISTS,按 perms IN 守卫)
- setval 尾(V14 纪律)

**1.2 服务层族守卫** `RepTempServiceImpl`:
- 私有助手 `requireScopeFamily(Integer type, String op)`:type=1 → 要求 `setting:temp:public:<op>`;type=2 → 要求 `setting:temp:<op>`;缺族 `throw new AccessDeniedException("缺少公共模板权限: setting:temp:public:<op>")`(或对应消息)→ AuthDenyAdvice A0301 信封;authorities 直读 `SecurityContextHolder.getContext().getAuthentication().getAuthorities()`
- 接线:getTree/getPage 在 type=1 时断言 public:list;create 在校验前按 request.getType() 断言;update/delete 在 requireOwnedRow 之后按行 getType() 断言(顺序 = 先归属后族,非 owner 私行保持 A0400)
- TDD 矩阵(delta 5 场景):仅基本族 → 公共写/公共读 A0301(content-free);仅公共族 → 公共写成功 owner_uid NULL;双族全通;非 owner 私行仍 A0400

**1.3 控制器钉放宽**:`RepTempController` 四个写端点 `@PreAuthorize` 改 `hasAnyAuthority('setting:temp:<op>', 'setting:temp:public:<op>')`(类门控不动);TDD:仅公共族用户过钉

**1.4 种子断言**:SettingSchemaSeedTests 补菜单/F 行断言前先 `git status` 看该文件是否仍在他会话脏集——是则**最小行数**(只加必须行)并记录;若结构不覆盖 F 节点则独立断言进 V41 相关测试或菜单测试文件

## TDD 顺序(铁律)
先写 perm 矩阵失败测试(AuthRepTempPermPinTests 或新 RepTempScopePermTests,跟随其 authorities 装配 idiom)→ RED 确认 → 最小实现 → GREEN → 存量三套件(AuthRepTempApiTests/AuthRepTempPermPinTests/AuthRepTempContentApiTests 13 用例)回归绿。

## 构建/测试
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
cd lumen-sev && ./mvnw -pl lumen-system -am test -q   # 必须 -am(陈旧 jar 教训)
```
共享测试库:断连重试一次后报 BLOCKED。日志重定向,报告引 exit+计数。

## 提交
pathspec 限定单提交(迁移+代码+测试),风格 `✨ feat(sev): ...`。

## 报告
`openspec/sdd/reports/rep-temp-scope-perms-batch1.md`(<80 行):实现/RED-GREEN 证据/文件清单/自评/concerns。回报 ≤15 行:Status + 一行摘要 + commit sha + 报告路径。
