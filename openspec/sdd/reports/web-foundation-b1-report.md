# Batch 1 报告 — web-foundation-auth-console(task 1.1)

> 执行:birdbro 实现代理 · 2026-11-04 · 遵循 `openspec/sdd/reports/web-foundation-b1-brief.md`
> 结论:**5 项验证全部通过(退出码全 0),任务 1.1 完成**。未 commit,未接线 mock 插件,
> 未触碰 src/ 业务文件与 openspec/ 规格。

## 1. 环境

| 项 | 值 |
|:--|:--|
| Node | v22.16.0(满足 engines `^20.19.0 \|\| >=22.12.0`,无需 nvm 切换) |
| pnpm | 10.32.1 |
| 执行方式 | 全程 `pnpm --dir lumen-web <cmd>`,未在仓库根跑 pnpm |

## 2. 依赖选型与版本(全部精确锁,无 `^`/`~`)

### 2.1 mock 插件选型(design D3)——**走主选,无需回退 msw**

- 命令:`pnpm --dir lumen-web add -DE vite-plugin-mock-dev-server`
- 结果:安装成功,**pnpm 全程无 peer 警告、无 peer 错误**(输出仅含常规进度与
  `Ignored build scripts: esbuild@0.28.2` 提示,详见 §5.1)。
- 硬证据(读自安装后的 `node_modules/vite-plugin-mock-dev-server/package.json`):

```json
{
  "version": "2.7.1",
  "peerDependencies": {
    "esbuild": ">=0.21.0",     // optional
    "rolldown": ">=1.0.0-beta.1", // optional
    "vite": ">=5.0.0",         // required
    "zstd-codec": ">=0.1.5"    // optional
  }
}
```

  required peer `vite: ">=5.0.0"` 包含现有 vite 7.3.7 → 选型维持 D3 主选
  `vite-plugin-mock-dev-server 2.7.1`,未触发 msw 回退分支。
- 按 brief 要求,**本批只装包不接线**;vite.config.ts 中无任何 mock 插件引用(接线属 task 3.1)。

### 2.2 新增依赖一览

| 包 | 版本 | 类别 |
|:--|:--|:--|
| zustand | 5.0.16 | runtime(dependencies) |
| vitest | 5.1.0 | devDependencies |
| jsdom | 30.1.3 | devDependencies |
| @testing-library/react | 16.3.3 | devDependencies |
| @testing-library/jest-dom | 7.0.1 | devDependencies |
| @testing-library/user-event | 14.6.8 | devDependencies(按 brief 预装,后续批次用) |
| vite-plugin-mock-dev-server | 2.7.1 | devDependencies |

兼容性证据(vitest 5.1.0 的 package.json):`peerDependencies.vite = "^6.4.0 || ^7.0.0 || ^8.0.0"`
(含 7.3.7)、`jsdom = "*"`;@testing-library/react 16.3.3 peers
`react/react-dom ^18.0.0 || ^19.0.0`(满足 18.3.1)、`@types/react ^18.0.0 || ^19.0.0`(满足 18.3.31)。
jest-dom 7.0.1 确认存在 `./vitest` 导出(setup.ts 的导入路径有效)。

自动 peer:RTL 16 声明 required peer `@testing-library/dom ^10.0.0`(不在 brief 安装清单内),
pnpm(auto-install-peers 默认开)自动以 **10.4.2** 装入虚拟 store,jest-dom/RTL/user-event
的 lockfile 解析串均显示 `(@testing-library/dom@10.4.2)`;测试与构建绿即为解析有效的证明。
清单(package.json)保持与 brief 清单一致,未额外声明直依赖。

## 3. 文件改动(全部,git status 实录)

| 文件 | 改动 |
|:--|:--|
| `lumen-web/package.json` | +`"test": "vitest run"` 脚本;+zustand 及 6 个 devDeps(精确锁)。现有 dev/build/preview 脚本原样 |
| `lumen-web/pnpm-lock.yaml` | +1261 行(lockfile 随安装更新) |
| `lumen-web/vite.config.ts` | +6 行:文件头 `/// <reference types="vitest/config" />` + `test: { environment: 'jsdom', globals: true, setupFiles: ['./src/test/setup.ts'] }`。**`build.rollupOptions` 与 `server.proxy` 未动**(diff 中仅作上下文出现) |
| `lumen-web/src/test/setup.ts` | 新建:`import '@testing-library/jest-dom/vitest'`(含说明注释) |
| `lumen-web/src/test/smoke.test.tsx` | 新建:2 个用例——`expect(true).toBe(true)` + `render(<div>hello</div>)` 后 `expect(screen.getByText('hello')).toBeInTheDocument()`(显式 vitest 导入,jest-dom 匹配器经 setup.ts 生效,证明 jsdom+RTL 管线全通) |

tsconfig 两份均未改动(strict 原样保留);冒烟测试用显式 `import { describe, expect, it } from 'vitest'`,
无需为 `globals: true` 调整 `types` 字段。

## 4. 五项验证(全部通过)

1. **install**:退出码 **0**(`Done in 192ms using pnpm v10.32.1`)。
2. **test**:退出码 **0**。

   ```
   RUN  v5.1.0 lumen-web
   Test Files  1 passed (1)
        Tests  2 passed (2)
     Duration  481ms (environment 74%, setup 16%, ...)
   ```
   (environment 占比 74% 证明 jsdom 真实启动;setup 占比 16% 证明 setupFiles 生效。)
3. **build**:退出码 **0**。`tsc --noEmit`(覆盖 src/,含新 test 文件,strict+noUnusedLocals 全过)
   → `tsc -p tsconfig.node.json --noEmit`(覆盖 vite.config.ts,证明 `/// <reference types="vitest/config" />`
   与 `test` 块类型有效)→ `vite build`(`✓ 1452 modules transformed`,`✓ built in 1.15s`)。
   仅有既有的 chunk >500kB 警告(非错误,先前批次已存在)。
4. **ls --depth 0**(证明精确版本,17 packages):

   ```
   dependencies:
   ├── antd@5.29.3  ├── react@18.3.1  ├── react-dom@18.3.1
   ├── react-router-dom@7.18.5  └── zustand@5.0.16
   devDependencies:
   ├── @testing-library/jest-dom@7.0.1  ├── @testing-library/react@16.3.3
   ├── @testing-library/user-event@14.6.8  ├── @types/node@22.16.5
   ├── @types/react@18.3.31  ├── @types/react-dom@18.3.7
   ├── @vitejs/plugin-react@5.2.0  ├── jsdom@30.1.3
   ├── typescript@5.9.3  ├── vite@7.3.7
   ├── vite-plugin-mock-dev-server@2.7.1  └── vitest@5.1.0
   ```
5. **package.json diff 复查**:`git diff -- lumen-web/package.json` 仅含新增行(test 脚本、
   zustand、6 个 devDeps);基线依赖**版本值无一变动**——react 18.3.1 / react-dom 18.3.1 /
   antd 5.29.3 / react-router-dom 7.18.5 / @types/node 22.16.5 / @types/react 18.3.31 /
   @types/react-dom 18.3.7 / @vitejs/plugin-react 5.2.0 / typescript 5.9.3 / vite 7.3.7。
   唯一非新增差异是 pnpm add 按字母序重排了既有键(见 §5.2)。

## 5. 遇到的问题(均不阻塞)

- **5.1 pnpm 10 的 `Ignored build scripts: esbuild@0.28.2` 提示**:pnpm 10 默认拦截依赖
  生命周期脚本,每次 install/add 都会提示。esbuild 平台二进制由 optionalDependencies
  (@esbuild/darwin-arm64)直接安装,postinstall 仅是校验/回退路径;验证 3 的 vite build
  成功即证明 esbuild 工作正常。未执行 `pnpm approve-builds`(不属本批范围)。
- **5.2 package.json 键序重排**:`pnpm add` 将 dependencies/devDependencies 重排为字母序
  (如 antd 移到 react 前)。纯格式变化,值未动;已逐项核对(§4.5)。
- **5.3 下载慢告警**两条(`@hapi/bourne`、`@csstools/css-color-parser` 低于 50KiB/s),
  网络抖动,安装均成功,无影响。
- **5.4 vitest 落在 5.1.0 大版本**(brief 未指定大版本,house 规约为"精确锁当前解析版"),
  其 vite peer `^6.4.0 || ^7.0.0 || ^8.0.0` 与现有 7.3.7 兼容,已留证据(§2.2)。

## 6. 完成标准对照

| brief 要求 | 状态 |
|:--|:--|
| 5 项验证全过 | PASS(§4,退出码全 0) |
| 报告写入本文件(中文) | 本文件 |
| 不 commit | 未 commit(git 仅工作区改动,由协调者评审提交) |
| 不动 mock 接线 / src/ 业务文件 / openspec/ 规格 | 仅新增 `src/test/{setup.ts,smoke.test.tsx}`(brief 明确要求),无业务文件改动;openspec/ 仅本报告 |
| 失败不放宽配置 | 无失败项;strict、setupFiles、既有 server/build 配置原样 |
