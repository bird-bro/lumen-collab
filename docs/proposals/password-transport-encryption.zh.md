# password-transport-encryption 提案说明（跨栈）

> ⚠️ 本文为非权威中文导读，帮助快速理解提案背景与下一步动作；英文 OpenSpec 工件
> （`openspec/changes/password-transport-encryption-{api,web}/`）是唯一权威来源。

## 这个提案解决什么

用户安全审查（2026-11-09）发现：登录接口 `POST /api/v1/system/login` 的请求体是明文
JSON —— `{"username":"admin","password":"admin123"}`，DevTools 与任何非 TLS 跳点上
直接可见。

**后端审计结论：存储层没有 bug。** `user_info.password` 存的是 BCrypt `$2a$10` 盐化哈希
（design D4），`AuthServiceImpl` 用 `passwordEncoder.matches` 校验，日志切面已排除
password 字段，Redis 会话不含哈希。缺口只在**传输层**。

用户裁决（2026-11-09）：用 **SM2 国密**做传输加密（医院部署可能面临商用密码应用
安全性评估/密评），参照 EIS 参考项目的 keyPair 先例，静态配置密钥对，干净切换。

## 设计一览（3 步协议）

```
前端                                    后端 lumen-system
 │ 1. GET /api/v1/system/auth-key(匿名)  │
 │ ────────────────────────────────────► │ {keyId, publicKey: 130位hex未压缩点}
 │ 2. sm-crypto: doEncrypt(明文,公钥,1)   │    (C1C3C2 模式, hex)
 │ 3. POST /system/login                 │
 │    {"username":..,"password":"<密文>"} │
 │ ────────────────────────────────────► │ Hutool SM2 解密 → 原始密码 → 既有 BCrypt
```

- 覆盖全部 3 个含密码端点：登录、创建用户（初始密码）、管理员重置密码
- **干净切换**：明文/非 hex/解密失败一律 `A0400`（复用 OPS `ExceptionEnum` 既有常量，
  逐字不改，与 logo-upload 用 A0400、权限用 A0301 同一先例），无双轨过渡
- 静态密钥（用户拍板）：`lumen.security.password-crypto.{key-id, sm2-private-key}`，
  私钥 = 32 字节 D 的 hex；公钥由私钥推导（单一配置源不漂移）；**私钥属敏感配置走
  env / gitignored application-dev.yml，绝不入库**（D8 规则）
- 唯一新依赖：后端 `bcprov-jdk18on`（Hutool SM2 底层）、前端 `sm-crypto`

## 变更登记（跨栈）

| Change | 栈 | 范围 |
|:--|:--|:--|
| `password-transport-encryption-api` | lumen-sev | `/auth-key` 端点 + 3 端点解密接线 + bom 加 BC + 测试密钥对与互通向量 |
| `password-transport-encryption-web` | lumen-web | `sm-crypto` + `password-cipher.ts`(取钥/缓存/加密) + 3 处调用点 |

协调文档：`openspec/coordination/password-transport-encryption.md`（决策 D1–D9 +
决策日志）。后端先行；前端对真后端开发（禁 mock 层，2026-11-05 用户裁决）。

## 关键文件与角色

**后端（lumen-sev）**
- `lumen-bom/pom.xml` — 加 `bcprov-jdk18on` 版本钉（唯一 pin，OPS 版本集其余不动）
- `lumen-system/.../service/support/PasswordCipherHelper.java`（新）— 包一个 Hutool
  `SM2` 实例；启动时缺私钥即 fail-fast；`decrypt(hex)` → 原始密码
- `lumen-system/.../controller/AuthController.java` — 加 `GET /auth-key`
  （匿名：`security.permit-all` 配置一行，与 /login 同机制）
- `lumen-system/.../service/impl/AuthServiceImpl.java` / `UserInfoServiceImpl.java` —
  解密步骤接在既有 BCrypt **之前**（BCrypt 行一字不动；A0400 短路发生在账号查询**之前**，
  明文探测无法枚举账号）
- `lumen-system/src/main/resources/application.yml` — 提交空默认 + 注释；测试密钥对
  只进 test resources

**前端（lumen-web）**
- `src/utils/password-cipher.ts`（新）— 取钥 + 模块级缓存 + `encryptPassword()`；
  遇 `A0400` 清缓存（下一次提交自动重取钥，无重试循环）
- `src/pages/login/index.tsx` — 提交前加密；输入的明文永远不进请求体
- `src/api/users.ts` — `createUser`/`resetPassword` 在 API 客户端边界内加密
  （对外签名保持明文参数，语义集中一处）

## 已拍板决策（2026-11-09）

| 决策 | 结论 |
|:--|:--|
| 算法 | SM2 国密（RSA-2048 → SM2 改判：密评前瞻 + Hutool 原生支持，摩擦仅 1 依赖 + 格式对齐测试）|
| 密钥管理 | 静态配置密钥对（轮换 = 换 key-id + 私钥重启；多实例天然共享）|
| 覆盖端点 | 全部 3 个（避免补丁式追加）|
| 明文处理 | 干净切换拒绝，无明文后门 |
| 错误码 | `A0400` 逐字复用（零 OPS 枚举改动）|
| 密文格式 | C1C3C2 + hex，**两侧互通固定向量单测钉死**（前端向量与后端字面量一致）|

## 下一步动作建议

1. 审阅两份 proposal / contract（`openspec/changes/password-transport-encryption-{api,web}/`）
2. `/opsx:apply password-transport-encryption-api` —— 后端先行（TDD：互通向量红→绿、
   集成测试 a–f、fail-fast 切片测试）
3. 后端用户验证通过后 → `/opsx:apply password-transport-encryption-web`
4. 联合冒烟（任务 3.2 两侧）：UI 登录走 DevTools 看密文体、明文 curl → `A0400`
5. `/opsx:verify` 双 PASS → 归档同步 specs（api / auth-rbac / web-auth-flow /
   web-system-console），跨栈门禁满足

## 流程外备注

- 同次安全审查还发现**登录接口无限流/失败锁定**（暴力破解面）—— 已明确列为范围外，
  建议作为独立提案排期
- `must_change_password=1` 的「首登强制改密」流程尚无端点 —— 本提案的
  `PasswordCipherHelper` 对未来该端点免费生效（同一 helper）
