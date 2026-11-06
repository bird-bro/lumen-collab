<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: org-logo-upload-api
  date: 2026-11-06
  overall: PASS
  stacks:
    lumen-sev:
      L1: PASS
      L2: "7/7"
      L3: PASS
---

# 验证结果: org-logo-upload-api

> 生成时间: 2026-11-06 · 总体: **PASS**

## lumen-sev

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q`(JDK 11,reactor)exit 0 |
| L2 规格对齐 | 7/7 已实现 | 0 PARTIAL / 0 NOT_IMPLEMENTED |
| L3 测试 | ✓ PASS | 26 suites / 107 tests / 0 failures / 0 errors(`./mvnw test -q` 全 reactor,含 OrgLogoUploadTests 7/7) |

## 场景对齐详情

Delta: `specs/api/spec.md`(MODIFIED Gateway Path Convention + ADDED Common Image Upload Endpoint)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Service routing(全部端点在 `/api/v1/<service>/**`) | ✓ 已实现 | CommonController.java:46 `@RequestMapping("/api/v1/system/common")` + :63 `@PostMapping("/upload/image")` — 完整网关路径约定 |
| Static read-back segment(`/profile/**` 匿名 GET、只读) | ✓ 已实现 | WebMvcConfig.java:46-53(RESOURCE_PREFIX 注册 + `file:${upload.home}/profile/` 定位);SecurityConfig.java:109(`GET /profile/** permitAll`);仅注册静态读,无写入面 |
| Upload succeeds(envelope 200 + `{path}` + 磁盘字节) | ✓ 已实现 | CommonController.java:63-91(校验→`FileUtils.uploadFile`:78→`pathMap.put("path")`:90);`yyyy/MM/dd` 目录 + uuid 文件名由 :78-79 组合;实测:真机上传 → `{path}` → 磁盘落盘一致(任务 2.2 冒烟) |
| Invalid image → A0400 且不落盘 | ✓ 已实现 | CommonController.java:65(空文件)、:69(扩展名白名单 png/jpg/jpeg/webp 大小写不敏感)、:72(2MB 上限)→ `BusinessException("A0400", ...)`;测试断言"拒绝后磁盘零写入" |
| Unauthenticated caller → 203 | ✓ 已实现 | SecurityConfig.java:112 `anyRequest().authenticated()` + AuthenticationEntryPointImpl;测试 tokenless POST → `"203"`(控制器逻辑前拦截) |
| Stored path readable back anonymously | ✓ 已实现 | WebMvcConfig.java:46-53 + SecurityConfig.java:109;测试匿名 GET 返回相同字节;真机冒烟 `200 image/png` 字节一致。*配置注*:相对 `./upload` 默认值的读写锚点随启动器 CWD 漂移(本次冒烟实证)——用户裁决 2026-11-06:dev 覆盖用绝对路径(application-dev.yml 已落),提交态默认保留 + 注释警示 |
| Other services unaffected(无 upload.home 保持 parity) | ✓ 已实现 | WebMvcConfig.java:40 `@Value("${upload.home:}")` 空默认 + :47 条件守卫(空 → 无 locations,OPS 原样);parity 单测断言空默认;其余 25 个测试套件全部上下文无 upload.home 照常通过 |

## 发现的问题

无阻塞、无 PARTIAL。

备注(非场景缺口,已由用户裁决收口):
- 相对 `upload.home` 的锚点歧义 → 绝对路径裁决已落 `application-dev.yml`(gitignored)+ 提交态注释 + 协调文档 D5 修订(org-logo-upload.md,2026-11-06)。
- 框架适应项 #7(WebMvcConfig 一行补全)已登记于 `openspec/coordination/org-logo-upload.md`。
