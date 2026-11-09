<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: login-lockout
  date: 2026-11-09
  overall: PASS
  stacks:
    lumen-sev:
      L1: PASS
      L2: "12/12"
      L3: PASS
---

# 验证结果: login-lockout

> 生成时间: 2026-11-09 · 总体: **PASS**

## lumen-sev

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q` exit 0(JDK 11,reactor) |
| L2 规格对齐 | 12/12 已实现 | auth-rbac delta 全部场景 ✓(SM2 既有 5 + 锁定新增 7),证据全为后端域内文件 |
| L3 测试 | ✓ PASS | 全 reactor `./mvnw test -q` exit 0 — lumen-system surefire **138 tests / 0 failures / 0 errors**(本会话末次代码修复后的全量运行:`LoginLockoutServiceTest` 5/5 + `AuthLoginTests` 14/14 含 6 新锁场景) |

## 场景对齐详情

### auth-rbac delta — Login and Token Issuance(modified)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Successful login(密文 + 计数清零) | ✓ 已实现 | `AuthServiceImpl.java:139`(成功路径 `clear`);`AuthLoginTests.java:55`(200 + JWT)+ `:301` 清零断言 |
| Invalid credentials → A0210 且计数递增 | ✓ 已实现 | `AuthServiceImpl.java:133-136`(BCrypt 不匹配 → `recordFailure` 先于 throw);`AuthLoginTests.java:75-87` |
| Plaintext A0400 且不计数 | ✓ 已实现 | `AuthServiceImpl.java:100`(解密最前,计数器零触及);`AuthLoginTests.java:102` + `:284`(明文尝试后 fail 键不存在) |
| Tampered ciphertext A0400 不计数 | ✓ 已实现 | 同一解密先序;`AuthLoginTests.java:121-133` |
| Key endpoint anonymous(不变) | ✓ 已实现 | `AuthController` /auth-key;`AuthLoginTests.java:135` |
| Unknown accounts never count / 不可被锁 | ✓ 已实现 | 锁检查位于账号查询**之后**(`AuthServiceImpl.java:128`,user==null 已先抛 A0201);`AuthLoginTests.java:271-282`(探针后 fail/lock 键均不存在) |
| Failure threshold locks;下次尝试(任意密码)A0211 先于 BCrypt | ✓ 已实现 | `AuthServiceImpl.java:128-130`(gate)→ `LoginLockoutService.java:77-90`(达阈值设锁键);`AuthLoginTests.java:250-269`(第 5 错仍 A0210、第 6 次连正确密码也 A0211 且 data null) |
| Locked account rejects the correct password | ✓ 已实现 | 同 gate(锁检查不验密码);`AuthLoginTests.java:260-268` |
| Lock expires automatically | ✓ 已实现 | Redis TTL 语义:`LoginLockoutServiceTest`(2s 键自然过期)+ 登录级 `AuthLoginTests.java:361-375`(2s 键 → A0211 → 2.5s 后 200) |
| Successful login clears the counter | ✓ 已实现 | `AuthServiceImpl.java:139`;`AuthLoginTests.java:301-322`(4 败 + 成功 → 键删 → 重新需满 5 败才锁) |
| Admin password reset clears the lock | ✓ 已实现 | `UserInfoServiceImpl.java:145`(reset 成功后 clear);`AuthLoginTests.java:324-359`(锁 → admin 重置 → 新密码立即登录 200) |
| Session carries the permission set(不变) | ✓ 已实现 | `AuthLoginTests.java:165`(perms 集断言)全绿 |

## 关键实现注记

- **检查序**(design D4):decrypt(A0400)→ 账号查询(A0201)→ enable(A0202)→ **锁检查(A0211)**→ BCrypt(A0210+计数)→ 成功(clear)—— 未知用户名在锁检查前就返回,结构性保证"不可被锁";锁定账户耗一次 Redis GET 而非一次慢 BCrypt。
- **阈值语义**:第 5 次错试本身仍答 A0210(recordFailure 在 throw 前),锁从第 6 次起咬合 —— 测试 :250 显式钉住。
- **零 OPS 改动**:A0211 逐字复用;`UserLogin.isAccountNonLocked` 硬编码不动;`limitScript` bean 未消费(design D3 记录原因)。
- **前端零改动**:A0211 是 A-class envelope,走既有内联错误框渲染(本特征零 lumen-web 文件,git diff 归属核对过 —— lumen-web 的 2 个改动文件属并行会话 role-list-api)。

## 发现的问题

- 无阻断项。注记:并发下两次同时第 5 败会幂等重复设锁键(同结局,design D8 良性竞争);lumen-web 树上另有并行会话未提交改动(role-list-api),与本特征无关,归档/提交时注意归属。

## 下一步

- **PASS** → `/opsx:archive login-lockout`(specs 同步:auth-rbac Login and Token Issuance MODIFIED)。
