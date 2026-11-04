# Batch 1 Brief — web-foundation-auth-console（task 1.1）

> 执行者注意:本 brief 是唯一事实来源;规格在 `openspec/changes/web-foundation-auth-console/`
> (英文权威)。工作目录 `/Users/birdbro/work/02-birdBro/00-github/lumenCollab`(下称 REPO)。
> **不要 commit**——实现完成后写报告,由协调者评审后再提交。
> 端点前缀已是 `/api/v1/system/**`(并行更名已同步)——本批不涉及,仅作背景。

## 环境铁律
- 前端一律 `pnpm --dir lumen-web <cmd>`(lumen-web 有独立 lockfile/node_modules;不要在
  REPO 根跑 pnpm)。
- Node 版本须满足 `engines: ^20.19.0 || >=22.12.0`;先 `node --version` 确认,不满足则
  `source ~/.nvm/nvm.sh && nvm use 22`(或系统等效方式),报告记录实际版本。
- 全部依赖**精确锁版本**(`pnpm add -E` / `pnpm add -DE`),这是 house 规约;不得用 `^`/`~`。
- 现有依赖基线(勿动):react 18.3.1 / react-dom 18.3.1 / antd 5.29.3 /
  react-router-dom 7.18.5 / vite 7.3.7 / typescript 5.9.3 / @vitejs/plugin-react 5.2.0 /
  @types/react 18.3.31 / @types/react-dom 18.3.7 / @types/node 22.16.5。

## 任务 1.1 — dev-deps + zustand + Vitest 接线

1. **mock 插件选型(design D3)**:先装
   `pnpm --dir lumen-web add -DE vite-plugin-mock-dev-server`;若其 peer range 不含
   vite 7.3.7(pnpm 会报 peer 警告或失败),则改走 D3 回退:
   `pnpm --dir lumen-web add -DE msw`,并在报告中显式记录选型切换与版本。vite.config.ts
   的插件接线是 **task 3.1** 的范围,本批只装包不接线。
2. **测试栈**:`pnpm --dir lumen-web add -DE vitest jsdom @testing-library/react
   @testing-library/jest-dom @testing-library/user-event`(全部精确锁;user-event 一并装上,
   后续批次要用)。
3. **运行时 store**:`pnpm --dir lumen-web add -E zustand`。
4. **Vitest 接线**:
   - `lumen-web/vite.config.ts` 内加 `/// <reference types="vitest/config" />` 并补
     `test: { environment: 'jsdom', globals: true, setupFiles: ['./src/test/setup.ts'] }`
     (不动 server.proxy 与 build.rollupOptions——那是既有契约)。
   - 新建 `lumen-web/src/test/setup.ts`:`import '@testing-library/jest-dom/vitest'`。
   - `package.json` scripts 加 `"test": "vitest run"`(保留现有 dev/build/preview 不动)。
5. **占位冒烟测试**(防 "No test files found" 退 1):新建
   `lumen-web/src/test/smoke.test.tsx`,内容为最小断言(如 `expect(true).toBe(true)`
   级别 + 渲染一个 `<div>hello</div>` 用 @testing-library/react 的 render/screen,
   证明 jsdom+RTL 管线通)。

## 验证(全部留证据到报告)
1. `pnpm --dir lumen-web install` 退出 0。
2. `pnpm --dir lumen-web test` 退出 0(smoke 通过)。
3. `pnpm --dir lumen-web build` 退出 0(严格 TS 不被新依赖破坏)。
4. `pnpm --dir lumen-web ls --depth 0` 输出贴报告(证明精确版本)。
5. package.json diff 复查:现有依赖无一被改动版本。

## 完成标准
1. 上述 5 项验证全过。
2. 写批次报告到 `openspec/sdd/reports/web-foundation-b1-report.md`(中文,含命令输出摘要、
   选型结论、遇到的问题)。**不要 commit,不要动 mock/、src/ 业务文件、openspec/ 规格。**
3. 若任何验证失败:报告失败输出即止,不要为过关放宽配置(不得改 strict、不得去掉 setup)。
