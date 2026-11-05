# Tasks

## 1. 移除 dev-server mock 层（lumen-web）

- [x] 1.1 删除 `lumen-web/mock/` 目录（9 个文件：`_fixtures.ts` + auth/menus/users/roles/orgs/org-deps/dicts/logs）；验证：目录不存在，`grep -r "mock/" lumen-web/src lumen-web/vite.config.ts` 无对 mock 目录的引用
- [x] 1.2 `lumen-web/vite.config.ts` 移除 `mockDevServerPlugin` 的 import 与 `plugins` 挂载；验证：文件中无 `vite-plugin-mock-dev-server` 字样，`pnpm build` 通过（严格 TS 编译不回归）
- [x] 1.3 `lumen-web/package.json` 移除 devDependency `vite-plugin-mock-dev-server` 并重装锁文件；验证：`grep "mock-dev-server" lumen-web/pnpm-lock.yaml lumen-web/package.json` 无命中，`pnpm --dir lumen-web install` 成功

## 2. 回归与物理保证验证

- [x] 2.1 全量单测回归：`pnpm --dir lumen-web test` 全绿（vi.mock 模块打桩不受影响——18 个测试文件作为"删除无副作用"的证据）
- [x] 2.2 dev 链路实证：真后端入口可达时 `pnpm dev` 登录进壳，页面数据来自 proxy 目标（vite 终端无 mock 拦截日志）；后端入口不可达时页面请求如实失败（transport `B0001`），无任何假数据展示。验证：两种状态下各截图/记录终端输出
- [x] 2.3 mock 残留扫描：`grep -rn "mockDevServer\|_fixtures\|mock-only" lumen-web/ --include="*.ts" --include="*.tsx" -l`（排除 node_modules）零命中；`vi.mock`/`mockResolvedValue` 测试用语除外

## 3. 文档与约定同步（后端先行成文）

- [x] 3.1 根 `CLAUDE.md` Development Workflow 第 3 步重写：删除 "mock from spec"，改为后端先行串行流（backend Agent 实现 → 用户验 API → frontend Agent 对真后端开发；静态原型仅新页面）；同步 `AGENTS.md` 镜像。验证：两文件 diff 一致语义，不再出现 "mock" 指导词
- [x] 3.2 `lumen-web/CLAUDE.md` "Mock-First" 章节重写为 "Backend-First"：`pnpm dev` 前置=后端入口可达；"报页面正常 = 真后端数据"纪律；静态演示页边界（数据不经 `src/api/`、可识别为演示）。验证：章节内无任何 mock 工具指引
- [x] 3.3 `lumen-web/README.md`（如有 dev 说明段）同步启动顺序说明；验证：文档 grep 无 "vite-plugin-mock" 残留

## 4. Spec 对齐收尾

- [x] 4.1 `openspec validate frontend-backend-first` 通过（web-app-shell delta：Module Layout 修改 + Dev Server Serves Only Real Backend 新增）
- [x] 4.2 对照 spec WHEN/THEN 逐条核对 2.2 的实证记录；验证：四场景各有证据（dev 直连真后端 / 后端缺失如实失败 / mock 层不存在 / 演示页边界声明）
