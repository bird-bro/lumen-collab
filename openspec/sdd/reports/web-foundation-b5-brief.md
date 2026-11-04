# Batch 5 Brief(第 5 批 · 仅任务 5.1)— UI 原型(frontend-design skill)

> ⛔ **硬闸门**:本批只做任务 5.1(原型)。5.2–5.4(登录页/守卫/动态导航的 React 实现)
> 在**用户确认原型之前不得开始**——确认由协调者转达。本批交付物 = 静态 HTML 原型 + README,
> **不写任何 React/TS 生产代码**。

## 事实来源与创作约束(必读)

1. **先调用 `frontend-design` skill**(你有 Skill 工具:`Skill(skill: "frontend-design")`),
   按 skill 的流程产出。但注意:**美学方向已被规格钉死**,skill 的"选择大胆方向"步骤的
   答案就是下述约束;skill 的手艺指导(动效、空间、细节、打磨)在方向内应用:
   - 配色:OPS 深蓝系 —— 主色 `#3178fc`、导航 `#0f356a`/`#003676`、选中 `#007bc2`、
     hover `#32aef3`;正文 `#333`/`#666`/`#999`;边框 `#d7dbe2`;布局底 `#f3f5f7`;
     表头 `#f0f3fa`;斑马行 `#f6f6f6`。**红色永不做主色**(临床告警保留);不做紫色渐变。
   - 字体:MiSans(400/500/700)唯一品牌字体,18px 基准(16/18/20 体系);字重层级用
     500/700 区分。原型里用 `@font-face` 相对路径引用
     `../../../lumen-web/src/theme/fonts/MiSans-{Regular,Medium,Bold}.ttf`
     (原型目录在 `openspec/changes/web-foundation-auth-console/prototype/`,自行核对相对深度),
     回退栈 `PingFang SC, Microsoft YaHei, sans-serif`。
   - 布局:60px 头部(白底)+ 160px 深蓝侧栏 + 内容区;控制台页 = 左机构树 aside +
     右工作区(OPS 模式),表格页 = 搜索表单 + 表格 + 分页;弹窗 = 模态对话框。
   - 气质:**临床专业、克制、可信**——医疗影像协作平台;skill 的创意余地用在
     登录页的仪式感(品牌区/背景细节)、表格密度与留白节奏、微交互(hover/loading),
     不是用在推翻配色字体。
2. **内容用种子真值**(对照 `lumen-web/mock/_fixtures.ts`):管理员 `系统管理员`,
   医院 `示例医院`,科室 放射科/超声科/内镜科,字典 `common_status`(启用/停用),
   角色 `系统管理员`,菜单树 = 系统管理(机构/科室/用户/角色/菜单/字典/日志七项)+
   三科室占位。UI 文案用中文(与 mock 菜单名一致)。
3. **规格映射**:每个页面在 README 里对应到本变更的 scenario
   (`specs/web-auth-flow`、`specs/web-system-console`、`specs/web-design-system`)。

## 交付物(`openspec/changes/web-foundation-auth-console/prototype/`)

- `index.html` — 导航页:列出各原型 + 设计说明(方向、token 表、与 OPS 的对应)
- `login.html` — 登录页:居中卡片、产品名(LumenCollab / 影像协作平台)、账号+密码、
  Enter 提交提示、登录按钮 loading 态示意、错误消息位置示意(A 类错误文案内联展示一条:
  「用户密码错误」);**不放**隐私协议勾选与忘记密码(规格已裁掉)
- `console-shell.html` — 控制台壳:60px 头部(环境占位/用户区示意)+ 160px 深蓝侧栏
  (动态菜单树:系统管理展开 + 三科室)+ 内容区放用户管理页(搜索表单 + 表格
  [用户名/姓名/角色/启用开关/创建时间] + 分页 + 新建用户弹窗按钮)
- `org-deps.html` — 左侧机构树(示例医院)+ 右侧科室表格(放射/超声/内镜)+ 新建科室弹窗
- `role-menus.html` — 角色管理:角色表 + 「菜单授权」弹窗(可勾选树、全量替换保存按钮、
  重开显示已授权集的说明)
- `dicts-logs.html` — 字典(目录表 + 明细抽屉/表)与操作日志(过滤 + 只读表)一页双栏
- `README.md` — 设计说明 + scenario 映射表 + **「请确认」清单**(逐页列出需用户点头的点)

技术要求:纯静态 HTML + 内联/内嵌 CSS(可用 CSS 变量;原型目录不受 src/ hex 纪律约束,
但变量值必须与 tokens.ts 一致),无构建、无外部 CDN(字体本地相对路径);浏览器直接打开可用。
桌面 1440×900 为主视口,附一版窄屏说明(仅说明,不要求响应式实现)。

## 验证(留证)
1. 每个页面在浏览器可打开(用 `open` 或说明打开方式);字体经相对路径加载成功
   (报告给文件清单 + 大小)。
2. token 值与 `src/theme/tokens.ts` 一致(报告给对照)。
3. 未动:`lumen-web/src/**`、`mock/**`、`package.json`、`openspec/` 其他文件。
4. `pnpm --dir lumen-web test` 与 `build` 仍绿(零接触验证)。

## 完成标准
原型 + README 交付;**报告写 `openspec/sdd/reports/web-foundation-b5-report.md`**
(中文:skill 调用与方向决策、页面清单、token 对照、打开方式)。**不 commit**;
**不开始 5.2+**(协调者会把原型转呈用户,确认后另发 brief)。
