# Batch 2 Brief — web-foundation-auth-console（tasks 2.1–2.3：设计系统基座）

> 事实来源:brief + `openspec/changes/web-foundation-auth-console/` 的
> `specs/web-design-system/spec.md`(权威 WHEN/THEN)与 `design.md` D1/D2。工作目录 REPO
> `/Users/birdbro/work/02-birdBro/00-github/lumenCollab`。**不要 commit**(协调者评审后提交)。
> Batch 1 已就绪:vitest(jsdom/globals/setupFiles)可跑,`pnpm --dir lumen-web test`。

## 环境铁律
- 全程 `pnpm --dir lumen-web <cmd>`;Node ≥22(v22.16.0 已验证)。本批**零新增依赖**。
- 字体源(OPS 参考仓库,只读拷贝):
  `/Users/birdbro/work/01-JF/00-gitlab/OPS/ops_web/dist/assets/ttf/` 下
  `MiSans-Regular-BjbB2O6q.ttf`、`MiSans-Medium-CwKC24VQ.ttf`、`MiSans-Bold-DWPkCHEQ.ttf`。
  **MSYH.TTF 严禁拷入**(微软雅黑授权限制,spec 明文禁止)。
- hex 纪律:hex 字面量只允许出现在 `src/theme/tokens.ts`(以及新文件 `fonts.css` 里如有
  必要的 hex——目标是 fonts.css 零 hex;`src/test/` 里测试断言用的字面色不受限)。

## 任务 2.1 — MiSans 打包 + fonts.css

1. 拷贝三个 TTF 到 `lumen-web/src/theme/fonts/`,**重命名为干净名**:
   `MiSans-Regular.ttf` / `MiSans-Medium.ttf` / `MiSans-Bold.ttf`(记录文件大小到报告)。
2. 新建 `lumen-web/src/theme/fonts.css`:三条 `@font-face`,family 统一 `"MiSans"`,
   weight 400/500/700,`font-display: swap`,`src: url('./fonts/MiSans-*.ttf') format('truetype')`。
3. 在 `src/theme/index.ts` 里 `import './fonts.css'`(随 tokens 一起出货);若 App.tsx 的
   ConfigProvider 未消费 `themeTokens`,修正接线并报告。

## 任务 2.2 — TDD 重写 tokens(先红后绿)

1. **先写测试(RED)**:`lumen-web/src/test/tokens.test.ts` 断言 `themeTokens`:
   `colorPrimary === '#3178fc'`、`fontSize === 18`、`fontFamily` 以 `'MiSans'` 开头、
   `colorText === '#333'`。跑 `pnpm --dir lumen-web test` → **必须 RED**(现为青色
   `#0E7C86`/无 fontSize),把 RED 输出留证。
2. **hex 纪律守卫测试**(同批加入 `src/test/`):扫描 `src/**/*.{ts,tsx,css}`,
   排除 `src/theme/**` 与 `src/test/**`,断言无 `/#[0-9a-fA-F]{3,8}\b/` 字面量。
   此为常绿守卫(当前源码无违规),报告说明其守卫语义。文件收集用
   `import.meta.glob` 不可行(非 Vite 运行时),用 node:fs 递归 readdir 实现即可。
3. **重写 `src/theme/tokens.ts`(GREEN)**,OPS 映射(design D1):
   - token:`colorPrimary/colorInfo/colorLink = '#3178fc'`;`fontSize = 18`;
     `fontFamily = "'MiSans', 'PingFang SC', 'Microsoft YaHei', sans-serif"`;
     `colorText = '#333'`,`colorTextSecondary = '#666'`,`colorTextTertiary = '#999'`;
     `colorBorder = '#d7dbe2'`;`colorBgLayout = '#f3f5f7'`(OPS `--color-white-light`)。
   - components:`Layout: { siderBg: '#0f356a', headerBg: '#ffffff' }`;
     `Menu: { darkItemBg: '#0f356a', darkItemSelectedBg: '#007bc2', darkItemHoverBg: '#32aef3' }`;
     `Table: { headerBg: '#f0f3fa', rowHoverBg: '#f6f6f6' }`。
   - 主色浅色阶(colorPrimaryBg 等)交由 AntD 从 colorPrimary 自动派生,不手写。
   - 文件头注释更新:记录 OPS 取代青色 D6 的用户裁定(2026-11-03)+ 避红色原则保留 +
     "本模块是 src/ 内唯一 hex 所在地" 纪律。
4. 跑 test → 全绿(RED 转 GREEN 证据入报告)。

## 任务 2.3 — BasicLayout OPS 度量

1. 重排 `src/layouts/BasicLayout.tsx`:`Layout.Sider width={160}`(品牌块置顶,深蓝
   siderBg 由 token 出)+ **新增** `Layout.Header`,高度 60px(`style={{ height: 60,
   lineHeight: '60px', paddingInline: 24 }}`,背景走 token headerBg)。Header 内容本批
   仅占位(登出/用户区属 5.4 批次),不留空 div——放一个 `token.colorTextSecondary`
   的 `<span>` 环境名或留注释说明等待 auth 批次;Content 区与 Suspense/Outlet 原样保留,
   NAV_ITEMS 菜单本批**不动**(5.4 才换动态菜单)。
2. **先红后绿**:`src/test/layout.test.tsx` 先写断言——MemoryRouter 包裹渲染
   BasicLayout,`container.querySelector('.ant-layout-header')` 的 style.height 为
   `'60px'`、`.ant-layout-sider` 的宽度为 `'160px'`;ConfigProvider 用真实
   `themeTokens` 包裹。先跑确认对当前实现 RED(现无 Header/宽度非 160),再实现转 GREEN。

## 验证(留证到报告)
1. `pnpm --dir lumen-web test` 退出 0(新增 tokens/layout/hex-scan + 冒烟共 4 文件全绿)。
2. `pnpm --dir lumen-web build` 退出 0;**构建产物中字体被哈希打包**:
   `ls lumen-web/dist/assets | grep -i misans` 有 3 个文件。
3. `rg -n "#[0-9a-fA-F]{3,8}\b" lumen-web/src --glob '!src/theme/**' --glob '!src/test/**'`
   = 0 命中(hex 纪律独立复核)。
4. `rg -in "msyh|yahei.*\.ttf|微软雅黑.*ttf" lumen-web/src` = 0 命中;`ls lumen-web/src/theme/fonts/`
   恰好 3 个文件。
5. `git status --porcelain -- lumen-web/` 与本批范围一致(无 src/ 业务页、无 mock/、无 package.json)。

## 完成标准
1. 全部验证过;报告写 `openspec/sdd/reports/web-foundation-b2-report.md`(中文:RED→GREEN
   证据、文件清单、字体大小、遇到的问题)。不 commit。
2. 失败即停、如实记录,不放宽 strict/不删 setup/不给扫描测试开洞。
