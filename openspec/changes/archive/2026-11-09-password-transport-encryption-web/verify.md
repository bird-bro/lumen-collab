<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: password-transport-encryption-web
  date: 2026-11-09
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "9/9"
      L3: PASS
---

# 验证结果: password-transport-encryption-web

> 生成时间: 2026-11-09 · 总体: **PASS**

## lumen-web

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `pnpm build` exit 0(TS strict + vite 1.88s;chunk 警告为既有) |
| L2 规格对齐 | 9/9 已实现 | web-auth-flow delta 5 + web-system-console delta 4,全部 ✓(证据均为前端域内文件 —— 跨域禁令遵守) |
| L3 测试 | ✓ PASS | **249/249**(23 文件,exit 0;含 4 个 password-cipher 新用例 + 登录页/客户端密文体断言) |

## 场景对齐详情

### web-auth-flow delta — Login Page(modified)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Successful login(密文体 + 会话 + 路由) | ✓ 已实现 | `pages/login/index.tsx:88`(`encryptPassword` 先于 body 构建);`login-page.test.tsx:112-140`(CONSOLE_ORGS_MARKER + token 入 store + 线体 `^04[0-9a-f]+$` ≠ 明文 + 明文零出现) |
| Wrong password(A-class msg 内联、无会话) | ✓ 已实现 | `index.tsx:103-107`(catch → setError,OPS .error-info 机制未动);`login-page.test.tsx:142-157`(A0210 内联、无 token、无路由变化) |
| Enter submits(与按钮一致,且同样加密) | ✓ 已实现 | AntD Form onFinish 统一路径(未改);`login-page.test.tsx:159-173`(Enter → auth-key + login 两次 fetch、token 入 store) |
| Plaintext never on the wire | ✓ 已实现 | `index.tsx:88` + `utils/password-cipher.ts:47-50`(`'04' + doEncrypt(raw, pub, 1)`,输入值不进 body);测试断言 `not.toContain('"admin123"')` |
| Stale key surfaces as a message(A0400 自愈) | ✓ 已实现 | `index.tsx:99-103`(A0400 → `invalidateKeyCache()`)+ 内联 msg 同路径;`login-page.test.tsx:195-222`(A0400 内联显示 + 二次提交 auth-key 调用 ≥2 —— 无页面重载自愈) |

### web-system-console delta — User Management(modified)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Paged user list(不变) | ✓ 已实现 | `api/users.ts listUsers` 未动;api-clients `listUsers` 用例全绿 |
| Duplicate username feedback(A0111 照常浮现) | ✓ 已实现 | `api/users.ts:34-38,53-57` 仅拦截 `A0400`,其他 A-class 原样 rethrow → 页面 msg 路径不变(`pages/system/users/index.tsx:6` ApiError 引入未动);全套 249/249 含既有 A0111 用例 |
| Password and enable actions | ✓ 已实现 | `resetPassword`/`enableUser` 端点与签名不变;api-clients 对应用例全绿 |
| Encrypted password fields on create and reset | ✓ 已实现 | `api/users.ts:30,51`(客户端边界内加密,raw 签名不变 —— design D2);`api-clients.test.ts:166,185`(线体 `^04[0-9a-f]+$` ≠ 输入值、`"pw"`/`"n3wPass"` 零出现) |

## 关键实现注记

- **04 前缀由前端补**(协调 D4 精化):`password-cipher.ts:50` —— sm-crypto 原生输出 markerless,BC 引擎要求标准点编码;固定向量与后端字面量同一份(sm-crypto 0.3.13 产出、0.5.8 兼容实测)。
- **A0400 自愈是"下一提交重取"**(design D3):失效缓存一处调用,无自动重试循环 —— 测试 :195 精确钉住。
- 联合证据:api change verify.md 的 curl 冒烟(sm-crypto → 真后端 → JWT)与本 change 的 unit 线体断言合起来覆盖两侧;UI 层(浏览器 DevTools)属用户人工验证环节,见任务 3.2 注记。

## 发现的问题

- 无阻断项。过程注记:任务 3.2 UI 层(浏览器网络面板)待用户验证 —— 重启 :8081 后端 → 登录/建户/重置三链路 → DevTools 看 `04` 开头密文体;curl/unit 层证据已双栈闭环。

## 下一步

- **PASS** → 用户完成 UI 冒烟确认后:`/opsx:archive password-transport-encryption-api` + `/opsx:archive password-transport-encryption-web`(协调门禁:双 PASS 已满足,归档时同步 specs)。
