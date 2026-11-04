# Batch 5 报告(第 5 批 · 任务 5.1)— UI 原型(frontend-design skill)

> 变更:`web-foundation-auth-console` · 任务 5.1(仅原型闸门)。
> 交付物:`openspec/changes/web-foundation-auth-console/prototype/` 下 7 个文件。
> **未 commit;未开始 5.2+**(等用户经协调者确认原型后另发 brief)。

## 1. skill 调用与方向决策

- 已先通过 Skill 工具调用 `frontend-design`,再按 brief 的"方向钉死"约束执行:
  skill 的"选择大胆方向"步骤的答案即规格约束 —— **OPS 深蓝系、MiSans 唯一品牌字体、
  18px 基准(16/18/20)、60px 头部 + 160px 深蓝侧栏、临床专业克制可信、红色永不做主色**。
- skill 手艺的落点(方向之内):登录页仪式感(导航蓝渐变氛围 + 极淡"影像格网"纹理 +
  品牌区 + 错峰入场动效)、表格密度与留白节奏(表头 #f0f3fa / 斑马与行悬停 #f6f6f6 /
  表体 18px / 表头 16px 500)、微交互(输入聚焦环、菜单悬停 #32aef3、开关/弹窗过渡、
  登录 loading 态)。不推翻配色与字体体系,无紫色渐变,无外部 CDN。
- 原型为纯静态 HTML + 内嵌 CSS(少量演示用原生 JS:登录 loading/错误重放、弹窗开关、
  开关切换),双击即可打开,无构建。

## 2. 页面清单(目录 `openspec/changes/web-foundation-auth-console/prototype/`)

| 文件 | 大小 | 内容 |
|:--|--:|:--|
| `index.html` | 22,208 B | 导览页:方向说明、页面卡片链接、调色板色样、MiSans 字阶、布局度量、token 对照表、scenario 速览、窄屏说明 |
| `login.html` | 12,293 B | 登录页:居中卡片(LumenCollab / 影像协作平台)、账号+密码、Enter 提交提示、按钮 loading 态、A 类错误内联「用户密码错误」;无隐私协议勾选、无忘记密码 |
| `console-shell.html` | 21,639 B | 控制台壳:60px 头部(环境占位「开发环境」/ 用户区悬停退出登录)+ 160px 深蓝侧栏(系统管理展开 7 项 + 三科室占位)+ 用户管理(搜索表单 + 表格[用户名/姓名/角色/启用开关/创建时间] + 分页 + 新建用户弹窗) |
| `org-deps.html` | 20,119 B | 左机构树(示例医院根、悬停「新建下级」、底部「新建根机构」示意多根)+ 右科室表(放射科 radiology / 超声科 us / 内镜科 endo)+ 新建科室弹窗 |
| `role-menus.html` | 21,103 B | 角色表(系统管理员 · type 10 · seed 备注)+「菜单授权」弹窗(可勾选树、全量替换说明条、保存授权按钮、种子全集含按钮型「报告审核」) |
| `dicts-logs.html` | 20,718 B | 一页双栏:字典目录(common_status/通用状态)+ 明细(启用/停用,sort 升序);操作日志(类型/操作人/月份过滤 + 只读表 + 只读徽标) |
| `README.md` | 11,272 B | 设计说明、打开方式、token 对照、scenario 映射(Requirement/Scenario 级)、窄屏策略、逐页「请确认」清单、已知边界 |

合计约 129 KB;6 个 HTML 的 div/section/table 标签开闭已逐一核对平衡。

## 3. Token 对照(原型 ↔ `lumen-web/src/theme/tokens.ts`)

CSS 变量直接以所镜像的 AntD token 命名;前 14 行与 tokens.ts **逐项一致**:

| AntD token | 值 | 原型 CSS 变量 | 一致性 |
|:--|:--|:--|:--|
| colorPrimary / colorInfo / colorLink | `#3178fc` | `--colorPrimary` 等 | ✓ |
| colorText / colorTextSecondary / colorTextTertiary | `#333` / `#666` / `#999` | `--colorText*` | ✓ |
| colorBorder | `#d7dbe2` | `--colorBorder` | ✓ |
| colorBgLayout | `#f3f5f7` | `--colorBgLayout` | ✓ |
| fontFamily | `'MiSans','PingFang SC','Microsoft YaHei',sans-serif` | `--fontFamily` | ✓ |
| fontSize(基准) | `18`(16/20 为 OPS 字阶) | `--fontSize(SM/LG)` | ✓ |
| Layout.headerBg | `#ffffff` | `--Layout-headerBg` | ✓ |
| Layout.siderBg = Menu.darkItemBg | `#0f356a` | `--Layout-siderBg` / `--Menu-darkItemBg` | ✓ |
| Menu.darkItemSelectedBg | `#007bc2` | `--Menu-darkItemSelectedBg` | ✓ |
| Menu.darkItemHoverBg | `#32aef3` | `--Menu-darkItemHoverBg` | ✓ |
| Table.headerBg | `#f0f3fa` | `--Table-headerBg` | ✓ |
| Table.rowHoverBg(兼斑马行) | `#f6f6f6` | `--Table-rowHoverBg` | ✓ |
| Header 高 / Sider 宽(specs/web-design-system) | `60px` / `160px` | `--Header-height` / `--Sider-width` | ✓ 规格 |

原型额外使用、tokens.ts 未手写的值(实现时由 AntD 派生/默认,README 与 index.html 均已标注):
`#003676`(--navDeep,规格导航蓝家族,仅登录/导览背景渐变端)、`#4f95ff`
(--colorPrimaryHover,主按钮悬停,AntD 派生)、`rgba(49,120,252,.08)`(--colorPrimaryBg,
主色浅底)、`#ff4d4f`/`#52c41a`(AntD 默认错误/成功,仅语义)、`40px/6px/8px`
(AntD large 控件高与默认圆角)。

## 4. 种子真值对照(`lumen-web/mock/_fixtures.ts`)

- 用户 admin/系统管理员(启用,2026-11-04 09:00:00)为种子行,表格其余 3 行带「示意」角标;
- 角色 系统管理员(type 10,备注 seed 原文;第二行示意);机构 示例医院;
  科室 放射科 `radiology` / 超声科 `us` / 内镜科 `endo`;
- 字典 common_status/通用状态 → 启用(1)/停用(0),sort 1/2;
- 侧栏与授权树 = 种子菜单树(系统管理 7 子项 + 三科室 + 报告审核按钮节点),排序与 SEED_MENUS 一致;
- 登录错误文案 = `auth.mock.ts` `fail('A0210','用户密码错误')` 原文;
- 日志 3 行为 mock-only 演示数据,已注明后端日志表种子为空。

## 5. 打开方式与字体验证

```bash
open openspec/changes/web-foundation-auth-console/prototype/index.html   # 从仓库根目录
```
或直接双击任一 HTML。建议视口 1440×900。字体经 `@font-face` 相对路径
`../../../../lumen-web/src/theme/fonts/MiSans-{Regular,Medium,Bold}.ttf` 加载 ——
已用 `ls -L` 从原型目录实测三个 TTF 均可解析(brief 示例的 3 层 `../../../` 深度不够,
原型目录在仓库根下第 4 层,已按实际深度修正),回退栈 PingFang SC / Microsoft YaHei。
页面间侧栏与导览卡片可互相跳转;README 附逐页「请确认」清单。

## 6. 零接触验证

- `git status`:本批仅新增 `openspec/changes/web-foundation-auth-console/prototype/`;
  `lumen-web/src/**`、`mock/**`、`package.json` 及 `openspec/` 其他文件零改动。
- `pnpm --dir lumen-web test`:7 个测试文件 / 61 用例全部通过。
- `pnpm --dir lumen-web build`:1.21s 构建成功(500 kB chunk 提醒为既有现状,非错误)。

## 7. 问题与说明

1. **brief 字体相对深度修正**:brief 示例 `../../../lumen-web/...` 少一层,实际需
   `../../../../lumen-web/...`(prototype 在仓库根下第 4 层);brief 本身要求自行核对,已修正。
2. **斑马行与行悬停同值**(#f6f6f6):tokens.ts 中 Table.rowHoverBg 即该值,按 token 呈现,
   白底行悬停有反馈、斑马行上无附加变化 —— 与 OPS 一致,README 已注明。
3. **用户表未放「操作」列**(brief 只列了 5 列):重置密码等动作入口需用户给意见
   (列内链接 / 行展开 / 抽屉),已列入「请确认」清单。
4. **role type 10 原值直显**:不杜撰语义映射,是否需要文案映射交由用户确认。
5. **dicts-logs 一页双栏**为 brief 要求的原型展示形态;实现时为两个独立页面
   (/system/dict、/system/log),页面标题与副标题均已注明;明细区以"目录下方明细表"示意,
   抽屉形态留 6.6 决策。
6. **菜单管理 / 机构管理未单独出原型**(树/只读形态已由授权弹窗与机构树左栏覆盖),
   侧栏对应项置灰并附说明;守卫重定向、203 过期、按钮级 perms gating 属逻辑行为,
   静态原型不表达(5.3/5.4 范围)。
7. 未 commit;5.2+ 等待用户经协调者确认原型。

## 8. 修订 1(用户反馈:壳层布局对齐 OPS 三区;含修订 2)

用户反馈原型的「顶部 + 左侧菜单栏」与 OPS 不一致(原两区:AntD 深色 Sider),
且二/三级菜单显示方式不符。协调者已核 OPS 源码并给出规格事实,按此修订
(console-shell 为主,四个控制台页统一;index.html 与 README 同步):

### 修订 1 —— 三区壳层 + 分组标题菜单

- **区 1**:68px 深蓝图标主导航轨(最左,`--mainnav-background-color: #0f356a`,全高):
  顶部 logo 块;其下垂直排一级菜单(图标 + 下方短标签,`--mainnav-width: 68px` 内截断);
  选中 `#007bc2`、悬停 `#32aef3`。一级项 = 系统管理 + 放射科/超声科/内镜科。
- **区 2**:60px 白色头部横跨导轨右侧(`--header-height: 60px`):左产品标题
  「LumenCollab 影像协作平台」(26px/600,OPS 事实;MiSans 自托管仅 400/500/700,
  600 由浏览器落到 700);右用户区(头像/名 + 悬停出「退出登录」简化下拉);
  **不含 OPS 的问题反馈/客服/聆听图标**(OPS 业务)。
- **区 3**:头部下方 = 160px 白色二级侧栏(`--sidebar-width: 160px`,文字 `#6f6f6f`,
  可折叠 40px `--hide-sidebar-width`,Fold「收起」钮可点示意)+ 内容区横向 flex。
- **二/三级菜单渲染(关键)**:「分组标题 + 平铺菜单项」—— 分组标题「系统管理」
  (小号灰字 + 图标)+ 七个平铺项(顺序按 mock `SEED_MENUS` sort:用户管理 1 → 日志查询 7,
  协调者消息中的"机构/科室/用户/…"为枚举而非排序);科室类一级项为单页直接平铺项;
  非手风琴子菜单、非嵌套缩进树。
- **选中态选用**:采用 **OPS `--menu-background-active-color: #3c587d` 白字方案**
  (弃用「左侧主色条/浅蓝底」备选);二级菜单悬停底用 `#f0f3fa`(与表头 token 呼应)。
- 同步范围:index.html(hero、页面卡片、调色板 +#3c587d/#6f6f6f 两色、布局度量一节、
  token 表 +3 行、窄屏说明);README(修订记录、设计方向、token 表、页面清单、
  菜单一致性段、「请确认」清单)。

### 修订 2 —— 移除页头标题块(用户追加反馈,合并处理)

- OPS 列表页无 `<h1>`+副标题页头块(协调者核:`.page-container` 直接是 `.hpage-search` +
  `.add-btn-wrap` + 表格):四页统一删除,内容区直接以「工具行 → 表格 → 分页」开场;
  页面身份由侧栏选中项承载。
- 工具行 = 左(筛选输入/下拉 + 查询/重置)+ 右(主色新增钮);console-shell 的
  新建用户、org-deps 的新建科室(右侧列表工具行,附当前机构上下文)、role-menus 的
  新建角色均并入工具行右侧。
- 密度收紧:表头 11px、表体保持 13px 行距节奏(18px 表体 / 16px 表头不变),
  工具行/分页内边距收紧。
- dicts-logs 的「双栏同页示意,实现为 /system/dict 与 /system/log」说明移至右下角
  proto-chip,不再占页面标题位;index.html 设计说明加「列表页无页头标题块(OPS 紧凑风格),
  页面身份 = 侧栏选中项」;README「请确认」清单页头条目改为「已按 OPS 移除页头块,是否 OK」。

### 修订后验证与事实锚点

- 四个控制台页 + index 标签开闭平衡复核;字体相对路径不变;`git status` 仅 prototype/ 变更;
  `pnpm --dir lumen-web test`(61/61)与 `build` 复跑仍绿。仍未 commit;5.2+ 仍等用户确认。
- OPS 事实锚点(协调者提供):`var.scss` 的 `--mainnav-width:68px --header-height:60px
  --sidebar-width:160px --hide-sidebar-width:40px`;light 主题
  `--mainnav-background-color:#0f356a`、`--menu-background-color:white`、菜单文字 `#6f6f6f`、
  `--menu-background-active-color:#3c587d`(白字);layout/index.vue 三区注释
  「最左边侧边导航,一级导航栏」。
- 遗留待确认(已列入 README 清单):分组标题+平铺渲染、#3c587d 选中方案、
  折叠 40px 示意、无页头块 + 工具行新增钮位置。

## 9. 修订 3(侧栏 children 语义 · 工具行 · 图标操作列)

- **侧栏不重复一级(纠正修订 1 的一个错误指令)**:OPS Sidebar/index.vue +
  SidebarItem.vue 语义 —— 侧栏渲染**当前激活一级 section 的 children**:child 为叶子 →
  直接平铺项;child 为目录且有 children → 该 child 标题作分组标题。系统管理激活 →
  平铺项(无分组标题、无「系统管理」字样);科室类激活 → 单个平铺项。
  (该语义后被修订 7 的三分组结构具体化。)
- **工具行左侧 = 查询组件**:org-deps 删「当前机构/示例医院/种子科室 3 个」badge 行
  (机构上下文由左树选中态承载),左 = 科室编码/名称输入 + 查询;console-shell 左 =
  科室下拉(全部/放射/超声/内镜,对应 OPS el-tree-select 科室/部门)+ 查询;
  role-menus 左 = 角色名输入;dicts-logs 各面板工具行 = 编码/名称查询、目录选择、
  类型/操作人/月份。右侧统一 CirclePlus + 文字新增钮(OPS add-btn)。
- **操作列纯图标**(OPS user 页事实:el-button link + svg 图标,tooltip 出文字):
  用户/科室/角色/明细四表统一 铅笔=编辑、垃圾桶=删除(用户表另加 钥匙=重置密码,
  对应 spec 的 Password and enable actions;角色表加 树形=菜单授权),tooltip 中文;
  表头「操作」右侧带刷新小图标。新增按钮保留 icon+文字。删除一切信息 badge;
  日志「只读」语义移至表下注。

## 10. 修订 4(头部用户区对齐 OPS Header/index.vue)

- 触发区:左「用户名 / 所属机构」双行文字块(用户名 >6 字截断 …,OPS 规则);
  右 50×50 圆形头像(种子无头像图,首字占位「管」);**删除 caret 三角**(OPS 无)。
- 点击弹 popover(底部对齐,宽 250px,静态悬停示意):基本信息行(username + org)+
  「退出登录」= **plain 大按钮**(el-button plain size="large")整行呈现,非图标菜单行;
  个人中心类链接行 OPS 有、本产品暂无 —— 略去(记录在案)。
- **org 字段缺口(后端跟进)**:所属机构显示种子「示例医院」;profile 载荷暂无 org 字段,
  /me 或 profile 可能需补 org 字段 —— 已写入 README 数据真值与请确认清单。
- 其余三页头部同构,一并同步。

## 11. 修订 5(双色调配色 + 导轨尺寸 + 密度)

用户拍板:**OPS 结构 + lumen 青色操作**。协调者同步 tokens.ts 与规格,三方一致。
旧值 → 新值对照:

| 项 | 旧值(修订 1-4) | 新值(修订 5) |
|:--|:--|:--|
| colorPrimary / colorInfo / colorLink | `#3178fc` | **`#0E7C86`**(青 · 内容区动作色) |
| colorPrimaryHover | `#4f95ff`(AntD 派生) | **`#14A3B5`**(指定) |
| colorPrimaryBg | `rgba(49,120,252,.08)` | **`rgba(14,124,134,.10)`** |
| 二级侧栏选中 | `#3c587d` 白字(OPS light) | **青色左条 `#0E7C86` + 浅青底 `rgba(14,124,134,.10)`**(#3c587d 白字方案弃用) |
| 导轨 / 头部壳层 | `#0f356a` | `#0f356a`(不变) |
| 导轨选中 / 悬停 | `#007bc2` / `#32aef3` | 不变(`#007bc2` 仅导轨保留) |
| 表头 / 斑马 / 文本 / 边框 | `#f0f3fa` / `#f6f6f6` / `#333` / `#666` / `#999` / `#d7dbe2` | 不变 |
| 红色 | 仅错误语义 | 仅错误语义(永不做主色) |

- **导轨尺寸**(OPS layout/index.vue 实测 `.main-nav svg { font-size:26px }`):
  图标 26px、标签 ~16px;logo 块高 = 60px 头部同高。
- index.html 调色板与 token 表已标注双色调决策与理由;login.html 同步青色动作色
  (按钮/聚焦/品牌块;深蓝氛围背景保留,属壳层家族,背景光晕改用壳层家族色)。

## 12. 修订 6(分页居中)

OPS 事实:hpage.scss `.hpage-pagination { display:flex; align-items:center;
justify-content:center; height:60px }`(默认居中,可 start/center/end)。四张列表页
分页行改水平居中(高 60px);index/README 度量同步。

## 13. 修订 7(侧栏真实三级结构 —— 菜单结构决策)

- 用户要求「sidebar 中 2 级菜单是 3 级菜单的分组,和 OPS 一样」;原种子树系统管理下为
  7 个二级叶,无法呈现分组形态。**菜单结构决策(记录在案)**:系统管理下引入三个
  二级分组目录 —— **组织架构**(机构管理、科室管理)、**用户权限**(用户管理、
  角色管理、菜单管理)、**系统配置**(字典管理、日志查询)。
- 侧栏(系统管理激活)= 3 个分组标题 + 各组内平铺页面项,组间 1px 分隔线
  (OPS group 样式:组间分隔线、组有 padding-bottom);科室类一级项仍单页直达。
- **侧栏尺寸**(OPS layout/style/sidebar.scss 实测):菜单项 font-size 18px、行高 40px、
  图标 18px(右距 10px);分组标题 font-size 16px、组图标 20px;悬停/选中色按修订 5
  青色双色调;导轨 26px 复核未回退。
- 菜单授权弹窗树同步三级(系统管理 → 三分组 → 页面;放射科 → 报告审核;超声科;内镜科)。
- **后端跟进项**:该重构需同步 mock `SEED_MENUS` 夹具与后端 V2 种子
  (与 roles/list 并列);已写入 README 已知边界与请确认清单。
- index.html 增「菜单结构(修订 7 决策)」一节(层级表 + 跟进警示)。

## 14. 修订 8(内容区 padding)

main 内容区 padding = **10px**(修订 5 的 ~16px 级别再收紧,以用户指定 10px 为准),
四页统一;index/README 布局度量同步。

## 15. 修订 3-8 后复验

- 六页 div/section/nav/table 标签平衡复核通过;功能性旧色值清零
  (`#3178fc`/`#4f95ff`/`rgba(49,120,252,*)`/`#3c587d` 仅存于弃用标注与决策记录);
  字体相对路径不变;`git status` 仅 prototype/ 与本报告变更;
  `pnpm --dir lumen-web test`(61/61)与 `build` 复跑仍绿。仍未 commit;5.2+ 仍等用户确认。
- 新增待确认项:双色调配色、导轨 26px/16px、侧栏三级分组结构(含后端种子同步)、
  10px 密度、分页居中、图标操作列、工具行构成、头部用户区(org 字段缺口)。

## 16. 修订 9(三级 icon 区分 · 导轨纯白 · OPS 折叠形态 · 青色侧栏实验)

1. **二/三级 icon 区分**:二级分组标题**保留 20px 图标**(sidebar.scss
   `.el-menu-item-group__title svg { font-size:20px }`);三级页面项**全部去掉图标**
   (OPS SidebarItem 中组内子项 icon 条件渲染,实际菜单数据无 icon → 纯文字行)。
   四页侧栏共删除 28 个三级项图标;平铺项为 18px/40px 纯文字行。
2. **一级导轨**:图标精确 26px(复核未回退);图标与标签颜色由半透明白改为**纯白 #fff**
   (OPS `--mainnav-color: white`)。占位科室项保持降透明度以示「占位、非菜单数据」
   (记录在案)。
3. **折叠钮改 OPS 形态**:删除原「icon+收起文字」按钮。展开态 = **24px Fold 图标**
   绝对定位于侧栏右下角(right:4px / bottom:0 / padding:5px,无文字);收起态 =
   **40px 宽竖条**(Expand 图标 + 竖排文字 18px/600,width 18px 逐字换行,取当前激活
   一级「系统管理」;用户示例「机构管理」理解为竖排效果示意),整条可点击展开。
   原型提供两种状态:展开态实况 + 点击 Fold 后的收起态(可交互切换)。
4. **青色侧栏实验 variant**:console-shell 二级侧栏改 **#0E7C86** 底 + 白字,
   hover 半透明白;**选中态自定对比度安全搭配** —— 浅青片 #E6F6F7 + 深青字 #0E7C86 +
   亮青条 #14A3B5,理由:白字在浅青底上不满足 WCAG AA,深青对浅青片 ≈ 8.9:1;
   其余页面侧栏保持白色。README 标注为「青色侧栏实验 variant,待用户拍板后决定
   是否全站」。
5. index.html(菜单 icon 规则、折叠交互、实验标注入布局度量/菜单结构/页面卡片)、
   README(修订记录、布局说明、请确认清单)同步。

复验:六页标签平衡;四级折叠交互(foldBtn/expandStrip)两态可用;test(61/61)与
build 复跑绿;git 仅 prototype/ + 报告,未 commit。新增待确认:三级项无图标形态、
导轨纯白、折叠形态、青色侧栏 variant 去留。

## 17. 修订 10(青色侧栏实验裁定:回退白色)

用户裁定:**青色侧栏实验不采用**。执行:

- console-shell.html 侧栏从 `sidebar teal` variant 回退为与其余页一致的**白色侧栏**
  (文字 `#6f6f6f`);`.sidebar.teal` 相关 CSS **整段移除**(原型内不再保留该变体,
  本节即记录)。
- 选中态保留双色调青色系、仅底色回白:**青色左条 #0E7C86 + 浅青底 #E6F6F7 深青字** ——
  四页统一将 `--menu-active-bg` 定为字面量 `#E6F6F7`(原 rgba(14,124,134,.10) 与其
  混白结果几乎一致,按裁定改为具体值)。
- README:修订记录追加修订 10(修订 9 的实验条目保留为历史),布局说明与
  「请确认」清单的实验条目改为「已回退白色(用户 2026-11-04)」;index.html
  的页面卡片/布局度量/菜单结构表中实验标注同步改为回退说明。
- 其余不动;未 commit。修订 11(导轨背景色的用户决定)待协调者下达后另作修订。

## 18. 修订 11(导轨背景色:近黑科技蓝)

用户选定(2026-11-04),四页导轨统一:

| 项 | 旧值(修订 5-10) | 新值(修订 11) |
|:--|:--|:--|
| 导轨背景 | `#0f356a` | **`#0B2540`**(近黑科技蓝) |
| 导轨悬停 | `#32aef3` | **`#12355C`** |
| 导轨选中 | `#007bc2` | **`#0E7C86`**(品牌青,与主操作 colorPrimary 同色) |
| 图标 / 标签 | `#fff` / 26px / ~16px | 不变 |

- 原型以 rev-11 专用变量落地:`--mainnav-background-color` / `--mainnav-item-hover` /
  `--mainnav-item-active`;`--Layout-siderBg` / `--Menu-darkItemSelectedBg` /
  `--Menu-darkItemHoverBg` 保留为 tokens.ts 现值镜像(协调者正同步 tokens.ts 与规格,
  同步后对齐);导轨 logo 渐变同步改用新选中青 → 导轨底。
- index.html(lead、调色板色样与决策说明、token 表 +1 行)、README(修订记录、
  配色/布局条目、token 表、请确认清单 [x] 项)同步。
- 未 commit;test/build 复验同批次进行,保持绿。

## 19. 修订 12(主操作色回归亮蓝 #3178fc —— 单蓝色系)

用户裁定(2026-11-04,原话:button 用青色有些不协调):**主操作色青色退场,回归亮蓝**。
协调者同步 tokens.ts 与规格。旧 → 新对照:

| 项 | 旧值(修订 5-11) | 新值(修订 12) |
|:--|:--|:--|
| colorPrimary / colorInfo / colorLink(内容区动作色) | `#0E7C86`(青) | **`#3178fc`**(亮蓝,回归 tokens.ts 原值) |
| colorPrimaryHover | `#14A3B5`(青) | **`#5C97FD`**(指定) |
| colorPrimaryBg | `rgba(14,124,134,.10)` | **`rgba(49,120,252,.08)`**(AntD 派生) |
| 侧栏选中片 | 浅青底 `#E6F6F7` + 青条 | **浅蓝底 `#EBF2FF` + `#3178fc` 蓝条**(四页统一,文字转深蓝) |
| 导轨选中(修订 11 的青) | `#0E7C86` | **`#3178fc`**(跟随主操作) |
| 导轨 logo 渐变 | 青 → 导轨底 | **`#3178fc` → 导轨底** |
| 导轨背景 / 悬停(修订 11) | `#0B2540` / `#12355C` | 不变 |
| 聚焦环 / 阴影 | `rgba(14,124,134,*)` | `rgba(49,120,252,*)` |
| 红色纪律 | 仅错误语义 | 仅错误语义(不变) |

- 执行:四页 + login 全量值替换(功能引用清零,grep 复核 `#0E7C86/#14A3B5/#E6F6F7/
  rgba(14,124,134,*)` 仅存于历史注释);过时注释措辞同步更正。
- index.html(lead、调色板色样 +#EBF2FF、token 表多行、菜单结构表)与 README
  (修订记录、配色叙事改「近黑蓝壳 + 亮蓝操作,单蓝色系」、布局条目、token 表、
  请确认清单)同步。
- 叙事主线:修订 5-11 的青色为中间态,最终收敛为**单蓝色系** —— 近黑蓝壳
  (#0B2540) + 亮蓝操作(#3178fc)。
- 未 commit;test(61/61)与 build 复验保持绿(见修订 12 复验)。
