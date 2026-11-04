# Batch 7 报告 — web-foundation-auth-console(tasks 7.1–7.3:集成验证)

> 执行:协调者主会话直接完成(6b 后子代理通道停用)。状态:**三项全过,变更实现完成
> (19/19 任务)**。

## 7.1 全量套件 + 构建(frozen-lockfile 干净安装)

- `pnpm --dir lumen-web install --frozen-lockfile` → Done(锁文件与 manifest 一致)
- `pnpm --dir lumen-web test` → **19 文件 / 155 用例全绿**
- `pnpm --dir lumen-web build` → 退出 0(tsc strict ×2 + vite build)
- 备注:破坏性 `rm -rf node_modules` 被安全门拒绝(预期行为),以 frozen-lockfile 校验 +
  现场全量验证等效覆盖;真正全新 checkout 的复核可归 verify 阶段按需执行。

## 7.2 浏览器级 mock 冒烟走查(Playwright + 系统 Chrome channel,10/10 PASS)

脚本 `/tmp/b7-smoke.cjs`,dev server 5179(mocks),viewport 1440×900;测完 kill。

| # | 场景 | 结果 |
|:--|:--|:--|
| 1 | 未认证访问 `/` → `/login`,**零 /api/v1 请求** | PASS |
| 2 | admin 登录 → 落 `/system/orgs`(首个有权菜单) | PASS |
| 3 | 三区壳层实测:导轨 68px / 头部 60px / 侧栏 160px | PASS |
| 4 | 科室页:选中机构列出三种子科室 | PASS |
| 5 | 用户页:admin 行 + 重置密码/启用控件 | PASS |
| 6 | 角色页:授权弹窗回显已保存授权(15/15 勾选) | PASS |
| 7 | 菜单页:只读树 perms 可见(system:user:list、radiology:report:audit) | PASS |
| 8 | 字典页:点目录行 → 明细 sort 升序加载 | PASS |
| 9 | 日志页:行渲染 + 无操作列(append-only) | PASS |
| 10 | popover 退出 → /login + localStorage 会话清空 | PASS |

脚本调试中修的两处均为**脚本自身**问题(弹窗 checkbox 异步渲染等待;「取 消」两字按钮
自动空格)——产品代码零改动。

## 7.3 设计系统四场景核对

- 18px 基准 / `#3178fc` 主色(非红)/ 字体离线自托管 / 60-160 度量:
  `tokens.test.ts` + `layout.test.tsx` 断言全绿(单元级);
  浏览器级度量由 7.2 场景 3 实测;字体离线(无第三方 CDN 请求)由 `fonts.css` 相对路径
  静态核验(无绝对 URL)+ 走查全程无外部字体请求。

## 结论

`web-foundation-auth-console` 的 19 个 tracked 任务全部完成并有验证证据:
**变更实现完成,可进入 `/opsx:verify` L2 校验 → 归档确认。**

挂账(不在本变更内):
1. 后端小变更:`GET /api/v1/system/roles/list` 实现(角色页已按 typed client 消费,落地即通)
2. 后端跟进:V2 种子菜单三级重构(对齐 mock 分组 13/14/15)、profile 补 org 字段、
   用户编辑/删除端点(如产品需要)
3. 可选:mock 假-token 拒绝开关(浏览器级 203 演练);woff2 字体子集化;暗色主题
