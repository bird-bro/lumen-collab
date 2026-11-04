# Batch 2 报告 — web-foundation-auth-console(tasks 2.1–2.3:设计系统基座)

> 执行依据:`openspec/sdd/reports/web-foundation-b2-brief.md` + 权威 WHEN/THEN
> `openspec/changes/web-foundation-auth-console/specs/web-design-system/spec.md` + design.md D1/D2。
> 状态:**全部完成,5/5 验证通过**。未 commit(按 brief 留待协调者评审)。

## 一、TDD RED→GREEN 证据

### 2.2 tokens(先红)

先写 `src/test/tokens.test.ts`(未动任何实现),`vitest run` 实际输出(节选自完整日志):

```
FAIL  src/test/tokens.test.ts > themeTokens - OPS global tokens > resolves primary/information/link colors to the OPS blue #3178fc
AssertionError: expected '#0E7C86' to be '#3178fc' // Object.is equality
Expected: "#3178fc"   Received: "#0E7C86"

FAIL  src/test/tokens.test.ts > uses the OPS 18px base type scale
AssertionError: expected undefined to be 18

FAIL  src/test/tokens.test.ts > points the font stack at the bundled MiSans family
AssertionError: expected false to be true

FAIL  src/test/tokens.test.ts > themeConfig - OPS component tokens > styles the layout shell with the OPS navigation blues
AssertionError: expected { themeTokens: { …(3) }, …(1) } to have property "themeConfig.components.Layout.siderBg" with value '#0f356a'  (Received: undefined)

Test Files  1 failed | 2 passed (3)
Tests  9 failed | 3 passed (12)
```

RED 阶段共 9 失败:全局 token 四组断言(青色 `#0E7C86`、无 fontSize、无 fontFamily、无中性色)+ 组件 token 映射缺失;3 个通过的是冒烟测试(2)+ hex 守卫(1,常绿,见下)。旧实现确为青色 D6 基线,RED 属实。

### 2.2 tokens(转绿)

重写 `src/theme/tokens.ts`(OPS 全量映射 + `themeConfig` 导出)、`src/theme/index.ts`(挂载 fonts.css、再导出)、`src/App.tsx`(接线修正,见"问题 2")后:

```
Test Files  3 passed (3)
Tests  12 passed (12)
```

### 2.3 BasicLayout(先红)

先写 `src/test/layout.test.tsx`(MemoryRouter + 真实 `themeConfig` 的 ConfigProvider 包裹渲染),对旧实现运行:

```
FAIL  src/test/layout.test.tsx > renders a 60px-tall header
AssertionError: expected null not to be null          ← 旧实现无 Header

FAIL  src/test/layout.test.tsx > renders a 160px-wide navigation sider
AssertionError: expected '200px' to be '160px'        ← 旧 Sider 为 AntD 默认 200px

Test Files  1 failed (1)   Tests  2 failed (2)
```

### 2.3 BasicLayout(转绿)

重排 `src/layouts/BasicLayout.tsx`(`Layout.Sider width=160` + 新增 60px `Layout.Header`,背景分别由 `siderBg`/`headerBg` 组件 token 承担,零内联色值)后,全套通过(见"五、验证")。

### hex 守卫的"会咬人"证明(变异测试)

hex 守卫按设计是常绿测试(当前源码无违规,首轮即绿)。为证明它不是恒真断言,做了临时变异:
在 `src/hex-canary.ts` 写入 `export const canary = "#3178fc"` → 守卫 RED 且精确报位:

```
AssertionError: hex literals found outside src/theme (move them into src/theme/tokens.ts):
  hex-canary.ts:1 -> export const canary = "#3178fc" ...
Test Files  1 failed (1)
```

删除金丝雀文件后守卫恢复 GREEN。金丝雀文件已删除,工作区无残留。

### 守卫语义说明

`src/test/hex-scan.test.ts` 用 `node:fs` 递归 readdir 扫描 `src/**/*.{ts,tsx,css}`
(排除 `src/theme/**` 与 `src/test/**`;`import.meta.glob` 是 Vite 构建期变换,测试运行期不可用,brief 已排除该方案),按行匹配 `/#[0-9a-fA-F]{3,8}\b/`——与独立复核用的 `rg` 正则完全一致。任何命中都会以 `文件:行 -> 代码片段` 列表失败,逼颜色回归 `src/theme/tokens.ts` 单一归属地。测试文件自身在排除区,故断言里的字面色不会自噬。

## 二、文件清单

新增:
- `lumen-web/src/theme/fonts/MiSans-Regular.ttf`(拷贝自 OPS 参考仓库,见下)
- `lumen-web/src/theme/fonts/MiSans-Medium.ttf`
- `lumen-web/src/theme/fonts/MiSans-Bold.ttf`
- `lumen-web/src/theme/fonts.css`(三条 @font-face:400/500/700,`font-display: swap`,`format('truetype')`;**零 hex**;MSYH.TTF 未拷入)
- `lumen-web/src/test/tokens.test.ts`
- `lumen-web/src/test/hex-scan.test.ts`
- `lumen-web/src/test/layout.test.tsx`

修改:
- `lumen-web/src/theme/tokens.ts` — OPS 全量 token 映射 + 组件 token + `themeConfig`;头注释记录 2026-11-03 用户裁定(OPS 取代青色 D6)、避红原则保留、"src/ 内唯一 hex 所在地"纪律
- `lumen-web/src/theme/index.ts` — `import './fonts.css'`(字体随 token 一起出货)+ 再导出 `themeConfig`;陈旧"teal"注释同步更新
- `lumen-web/src/App.tsx` — ConfigProvider 接线修正(见"问题 2")
- `lumen-web/src/layouts/BasicLayout.tsx` — 160px Sider + 60px Header;新增导出常量 `HEADER_HEIGHT = 60` / `SIDER_WIDTH = 160` 作为度量单一出处(spec § OPS Layout Metrics 的 "trace to a single shared definition");Header 内容为占位 span(`token.colorTextSecondary` 色,显示 `import.meta.env.MODE` 环境名,注释标明用户区/登出待 5.4 auth 批次);`NAV_ITEMS`、Content/Suspense/Outlet 原样未动

未触碰(遵守约束):`src/pages/**` 业务页、`mock/`、`package.json`、`openspec/` 下并行会话脏文件(`loop-state.yaml`、`rename-auth-to-system/**`、`specs/**` 均未动;本文报告文件除外)。

## 三、字体文件大小

| 源文件(OPS 参考仓库,只读) | 目标文件 | 字节 |
|:--|:--|--:|
| `MiSans-Regular-BjbB2O6q.ttf` | `src/theme/fonts/MiSans-Regular.ttf` | 8,073,152 |
| `MiSans-Medium-CwKC24VQ.ttf` | `src/theme/fonts/MiSans-Medium.ttf` | 8,052,924 |
| `MiSans-Bold-DWPkCHEQ.ttf` | `src/theme/fonts/MiSans-Bold.ttf` | 7,959,920 |

合计 24,085,996 字节(约 23 MB)。三份拷贝均通过 `cmp` 字节级一致性校验;`src/theme/fonts/` 恰好 3 个文件,无 MSYH/雅黑字体文件。

## 四、遇到的问题

1. **`pnpm build` 首跑失败(TS18048),以收紧类型而非放宽测试解决**:`ThemeConfig['token']` 是对可选字段的索引访问,类型天然带 `| undefined`;旧 `tokens.ts` 同样用此注解,只是从无人解引用所以从未暴露。tsc 报 `themeTokens is possibly 'undefined'`(10 处,全在测试文件)。修复:在 `tokens.ts` 定义 `type ThemeToken = NonNullable<ThemeConfig['token']>` 并以此标注导出——生产类型更精确(该映射恒为对象),测试断言一字未改,strict 未放宽。修复后 build 退出 0。
2. **App.tsx 接线修正(brief 2.1 预案命中)**:原 `<ConfigProvider theme={{ token: themeTokens }}>` 只消费 token 切片;2.2 引入 Layout/Menu/Table 组件 token 后,若维持旧接线则 siderBg/headerBg/darkMenu/Table 全部落不了地。改为 `<ConfigProvider theme={themeConfig}>`(`themeConfig = { token: themeTokens, components }`,`themeTokens` 引用同一对象)。已按 brief 要求在此报告备案。
3. **fontFamily 前缀断言的引号细节**:CSS 栈首族名带引号(`'MiSans', 'PingFang SC', ...`),直接 `startsWith('MiSans')` 恒为 false;测试内先剥离引号再断言前缀,语义与 brief 一致("以 MiSans 开头"),已在测试注释中说明。
4. **既有构建告警(非本批回归)**:`index-CPOWShdR.js` 515.10 kB 触发 Vite >500 kB chunk 告警(antd+react 主包),batch 1 起即存在,exit 0 不受影响;分包优化不在本批范围。
5. **dist 产物文件名观察**:构建产物 `MiSans-Bold-DWPkCHEQ.ttf` 等恰好与 OPS 源文件哈希后缀同形(同内容同哈希方案),属正常 Vite content-hash 行为,非误拷旧名;源文件已用干净名入库。

## 五、验证结果(5/5 通过)

1. **测试**:`pnpm --dir lumen-web test` 退出 **0**——4 个测试文件(tokens / layout / hex-scan / smoke)全绿,**14/14** 通过。
2. **构建**:`pnpm --dir lumen-web build` 退出 **0**(tsc --noEmit 双 tsconfig + vite build);`ls lumen-web/dist/assets | grep -i misans` 恰好 **3** 个文件(Regular/Medium/Bold,字体已被哈希打包)。
3. **hex 纪律独立复核**:`rg -n "#[0-9a-fA-F]{3,8}\b" lumen-web/src --glob '!src/theme/**' --glob '!src/test/**'` → **0 命中**(rg 退出 1 = 无匹配)。
4. **MSYH 禁令复核**:`rg -in "msyh|yahei.*\.ttf|微软雅黑.*ttf" lumen-web/src` → **0 命中**;`ls lumen-web/src/theme/fonts/` 恰好 **3** 个文件(fontFamily 栈里的 "Microsoft YaHei" 仅为系统回退名,无 `.ttf` 引用,rg 逐字确认)。
5. **范围复核**:`git status --porcelain -- lumen-web/` = 4 修改(App.tsx、BasicLayout.tsx、theme/index.ts、theme/tokens.ts)+ 5 新增(3 个测试文件、fonts.css、fonts/ 目录);无业务页、无 `mock/`、无 `package.json`;`lumen-web/dist` 为 gitignore 项。

## 六、完成标准自查

- [x] 全部验证通过,本报告即交付物;未 commit
- [x] 失败即停:build 首跑 TS18048 如实记录并以收紧类型修复,未放宽 strict / 未删 setup / 未给扫描测试开洞
- [x] TDD:tokens 与 layout 均有 RED 证据后才实现;hex 守卫另附变异(金丝雀)证明
- [x] 零新增依赖(package.json 未动);MSYH.TTF 未拷入
