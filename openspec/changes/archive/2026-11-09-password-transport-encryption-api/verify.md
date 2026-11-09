<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: password-transport-encryption-api
  date: 2026-11-09
  overall: PASS
  stacks:
    lumen-sev:
      L1: PASS
      L2: "16/16"
      L3: PASS
---

# 验证结果: password-transport-encryption-api

> 生成时间: 2026-11-09 · 总体: **PASS**

## lumen-sev

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q` exit 0(JDK 11,reactor 全绿) |
| L2 规格对齐 | 16/16 已实现 | api delta 10 场景 + auth-rbac delta 6 场景,全部 ✓ |
| L3 测试 | ✓ PASS | lumen-system surefire **125 tests / 0 failures / 0 errors**(exit 0) |

## 场景对齐详情

### api delta — Authentication Endpoints(modified)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Login endpoint documented and reachable(密文凭据) | ✓ 已实现 | `AuthController.java:60-61` → `AuthServiceImpl.java:100,113`(解密后既有 BCrypt);测试 `AuthLoginTests.java:54`;curl 冒烟 sm-crypto 加密 → 200 + JWT |
| Current-user endpoint(/me 不变) | ✓ 已实现 | `AuthController.java:85-87`;`meWithAdminTokenReturnsProfileMenusAndPerms` 全绿 |
| Auth-key endpoint is anonymous | ✓ 已实现 | `AuthController.java:71-79` + `application.yml:72`(permit-all 同 /login 机制);测试 `AuthLoginTests.java:134`(tokenless → 200,keyId + 04 前缀 130 位点) |
| Login password field carries ciphertext(明文拒绝) | ✓ 已实现 | `AuthServiceImpl.java:100` 解密先行(账号查询**之前**);明文 curl → `A0400 密码解密失败`;测试 `AuthLoginTests.java:101,120` |

### api delta — User Management Endpoints(modified)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Paged user list(不变) | ✓ 已实现 | `UserInfoServiceImpl.listUsers`;`AuthUserManagementTests` 全绿 |
| Create user(解密后初始密码 BCrypt) | ✓ 已实现 | `UserInfoServiceImpl.java:97`(decrypt → encode,插行前);加密建户集成测试全绿 |
| Duplicate username(不变 A0111) | ✓ 已实现 | 服务级 A0111 前置校验未动;测试全绿 |
| Password reset and enable toggle(不变) | ✓ 已实现 | enable 分支未动;reset 密文路径 `UserInfoServiceImpl.java:129` |
| Encrypted password fields | ✓ 已实现 | `:97`(create)+ `:129`(reset)同一 helper;`AuthUserManagementTests` 密文体变体全绿 |
| Plaintext or undecryptable rejected(no row change) | ✓ 已实现 | `PasswordCipherHelper.java:99-107` 统一 A0400;解密失败先于 insert/updateById(`:97`/`:129` 均在写行之前,结构性保证);失败形态单测 `PasswordCipherHelperTest.java:113,122,131`(markerless/篡改/异钥)+ 登录端集成证实同一路径 |

### auth-rbac delta — Login and Token Issuance(modified)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Successful login(密文) | ✓ 已实现 | `AuthLoginTests.java:54-71`(200 + JWT + filter 可验 + profile 无 password 字段) |
| Invalid credentials → A0210(密文错误密码) | ✓ 已实现 | `AuthLoginTests.java:74-87`(解密成功但 BCrypt 不匹配 → A0210 语义保持) |
| Plaintext password rejected(clean cutover,不泄露账号存在性) | ✓ 已实现 | `AuthLoginTests.java:101-118`;`:115` 显式断言 ≠ A0201(格式门先于账号查询,探测无法枚举账号) |
| Tampered ciphertext rejected → A0400 | ✓ 已实现 | `AuthLoginTests.java:120-132`(末位翻转 → A0400,无 token) |
| Key endpoint is anonymous(派生公钥) | ✓ 已实现 | `AuthLoginTests.java:134-143`(keyId=sm2-test + 04 前缀 130 位);公钥由 D·G 推导 `PasswordCipherHelper.java:151-153` |
| Session carries the permission set(不变) | ✓ 已实现 | `meWithAdminToken` 断言 perms 集(含 system:user:list / system:log:list)全绿 |

## 互操作固定向量(协调 D4)

`PasswordCipherHelperTest.java:59`(**sm-crypto 0.3.13 产出 → Hutool 5.8.20 解密逐字节一致**,C1C3C2-hex,线格式 = `04` + sm-crypto 原生输出);0.5.8 兼容性实测通过。冒烟:独立实例 :18081,匿名取钥 → 明文 curl A0400 → **live sm-crypto 加密 admin123 → 200 + JWT + uid 1**(真实跨库线格式证明)。

## 发现的问题

- 无阻断项。两条过程性注记:
  1. 任务 3.2 的 UI 层(浏览器 DevTools 看密文体)属与 `-web` change 的联合冒烟,curl/unit 层证据已闭环 —— 归档前随 web change 一并完成。
  2. create/reset 端点级"明文拒绝"无专用集成测试(与登录同一 helper A0400 路径,结构性覆盖 + 失败形态单测)—— 后续如加端点测试属锦上添花,非缺口。

## 下一步

- **PASS** → `/opsx:archive password-transport-encryption-api`(建议先完成 `-web` change 的验证与联合 UI 冒烟,按协调文档门禁双 PASS 后归档)
