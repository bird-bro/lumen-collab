# Tasks

> 纯规格措辞 change：零代码。验收 = delta 写齐 + strict 校验通过 + 归档同步后主规格 grep 复查。

## 1. Delta 与文档

- [x] 1.1 `specs/service-topology/spec.md`：MODIFIED "Verbatim Adoption of the OPS Base
  Modules"（可演进 + 加法式 + 新场景）与 "Recorded Adaptations to the Base Modules"
  （清单按裁定增长，六项，新增适配 (6) 场景）。
- [x] 1.2 `specs/environment-contract/spec.md`：MODIFIED "Externally Provisioned Data
  Services"（仅 PG + Redis，无注册/配置中心）与 "Connection via Environment, Never
  Committed"（env 变量或 gitignored profile，随访模式）。
- [x] 1.3 `docs/proposals/spec-hygiene.zh.md` 中文提案导读（非权威声明 + 路径 + 下一步）。

## 2. 校验与归档

- [x] 2.1 `openspec validate spec-hygiene --strict` exit 0。
- [ ] 2.2 归档同步后全规格 grep 复查：主规格无 "Nacos 配置中心"残留措辞（历史决策记录性
  提及除外）、"verbatim-only" 语义清零。
