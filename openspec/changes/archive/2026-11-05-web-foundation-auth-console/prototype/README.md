# UI 原型 · web-foundation-auth-console(任务 5.1)

> 静态 HTML 原型,供用户确认后再进入 5.2+(登录页 / 守卫 / 动态导航的 React 实现)。
> 纯 HTML + 内嵌 CSS(+ 少量演示用原生 JS),无构建、无外部 CDN;字体走本地相对路径。
> **本文为确认材料,非权威规格;`openspec/` 下的 spec 始终是唯一事实来源。**

## 修订记录

- **修订 1(用户反馈)**:壳层从两区(AntD 深色 Sider)改为 **OPS 三区结构** ——
  68px 深蓝图标导轨(一级菜单:图标 + 下方短标签,选中 `#007bc2` / 悬停 `#32aef3`)
  + 60px 白色头部(产品标题 26px/600;不含 OPS 的问题反馈/客服/聆听图标)
  + 160px 白色二级侧栏(文字 `#6f6f6f`,可折叠 40px,Fold 钮示意);
  二/三级菜单渲染改为 **「分组标题 + 平铺菜单项」**(非手风琴、非嵌套缩进树);
  二级菜单选中态**选用 OPS `--menu-background-active-color: #3c587d` 白字方案**
  (弃用浅蓝底备选)。
- **修订 2(用户追加反馈)**:删除列表页 h1 页头标题块(OPS 紧凑风格),内容区直接以
  「工具行(筛选 + 查询 左 / 主色新增钮 右)→ 表格 → 分页」开场;密度收紧
  (表头 11px / 表体 13px 行距节奏);页面身份由侧栏选中项承载。
- **修订 3(用户反馈)**:侧栏不重复一级菜单 —— 渲染激活一级 section 的 children
  (OPS Sidebar/index.vue + SidebarItem.vue);工具行左侧 = 查询组件、右侧 = CirclePlus
  新增钮;表格操作列改纯图标链接钮(tooltip 出文案,表头「操作」右侧带刷新图标);
  删除信息 badge(机构上下文由左树选中态承载)。
- **修订 4(用户反馈)**:头部用户区对齐 OPS Header/index.vue —— 左「用户名 / 所属机构」
  双行文字块(用户名 >6 字截断 …)+ 右 50px 圆头像(首字占位),无 caret;点击弹 popover
  (基本信息 + plain 大按钮「退出登录」;个人中心类链接行 OPS 有、本产品暂无,略去)。
- **修订 5(用户拍板)**:双色调 —— 壳层保持深蓝(#0f356a / #007bc2 / #32aef3),
  内容区动作色全面换青 **#0E7C86**(hover **#14A3B5**);侧栏选中改「青色左条 + 浅青底」
  (替换修订 1 的 #3c587d 白字方案);导轨图标 26px / 标签 ~16px。协调者同步 tokens.ts。
- **修订 6(用户反馈)**:分页行水平居中、高 60px(OPS `.hpage-pagination`)。
- **修订 7(用户反馈,菜单结构决策)**:系统管理下引入三个二级分组目录 ——
  **组织架构**(机构管理、科室管理)、**用户权限**(用户管理、角色管理、菜单管理)、
  **系统配置**(字典管理、日志查询);侧栏 = 分组标题 + 组内平铺项,组间 1px 分隔线;
  授权弹窗树同步三级结构。⚠ 需同步 mock `SEED_MENUS` 与后端 V2 种子
  (后端跟进项,与 roles/list 并列);科室类一级仍单页直达。
- **修订 8(用户指定)**:main 内容区 padding = **10px**(四页统一)。
- **修订 9(用户反馈)**:① 二/三级 icon 区分 —— 二级分组标题保留 20px 图标,
  三级页面项**无图标**(纯文字行,OPS SidebarItem 条件渲染、菜单数据无 icon);
  ② 一级导轨图标 26px、图标与标签改**纯白 #fff**(OPS --mainnav-color);
  ③ 折叠改 OPS 形态 —— 展开态 = 24px Fold 图标绝对定位于侧栏右下角
  (right:4px / bottom:0 / padding:5px,无文字),收起态 = 40px 竖条
  (Expand 图标 + 竖排文字 18px/600),整条可点击展开;
  ④ 青色侧栏实验 variant(历史:console-shell 曾临时换青色底,见修订 10 裁定)。
- **修订 10(用户裁定)**:**青色侧栏实验不采用,回退白色**(用户 2026-11-04)——
  console-shell 侧栏与其他页一致回白,`.sidebar.teal` variant 整段移除;
  选中态保留双色调青色系:青色左条 + 浅青底 **#E6F6F7** 深青字(四页统一)。
- **修订 11(用户选定)**:导轨背景色改**近黑科技蓝** —— 背景 #0f356a → **#0B2540**、
  hover #32aef3 → **#12355C**、选中 #007bc2 → 跟随主操作(修订 12 定为 **#3178fc**);
  图标/标签维持 #fff 26px 不变;协调者同步 tokens.ts 与规格。
- **修订 12(用户裁定)**:**主操作色青色退场,回归亮蓝 #3178fc**(用户原话:button 用
  青色有些不协调)—— 内容区动作色 #0E7C86 → **#3178fc**、hover #14A3B5 → **#5C97FD**;
  侧栏选中 = 浅蓝底 **#EBF2FF** + **#3178fc** 蓝条(四页统一);导轨选中跟随主操作改
  **#3178fc**(修订 11 的 #0B2540/#12355C 不动),logo 渐变同步换蓝;
  青色系(#0E7C86/#14A3B5/#E6F6F7)功能性引用清零,注释保留历史。**单蓝色系:
  近黑蓝壳 + 亮蓝操作**。

## 如何打开

- 直接双击任一 `*.html`,或:
  ```bash
  open openspec/changes/web-foundation-auth-console/prototype/index.html   # 从仓库根目录
  open openspec/changes/web-foundation-auth-console/prototype/login.html
  ```
- 建议视口 **1440×900**;字体通过 `@font-face` 相对路径
  `../../../../lumen-web/src/theme/fonts/MiSans-{Regular,Medium,Bold}.ttf`
  加载(原型目录在仓库根下第 4 层,已按实际深度核对;brief 示例中的 3 层路径不够),
  回退栈 `PingFang SC, Microsoft YaHei, sans-serif`,完全离线可用。
- 各页侧栏 / 导览页卡片可互相跳转;浏览器滚动、悬停、弹窗、开关均为可点演示。

## 设计方向(规格钉死,skill 手艺在此方向内应用)

- **气质**:临床专业、克制、可信 —— 医疗影像协作平台。深蓝即"影像读片室的冷静",
  留白与密度按表格工具节奏处理;动效只做入场一次的错峰淡入、悬停反馈与弹窗过渡。
- **配色(修订 12 单蓝色系:近黑蓝壳 + 亮蓝操作)**:壳层深蓝体系(修订 11:导轨背景
  **#0B2540** 近黑科技蓝、悬停 **#12355C**、选中 **#3178fc** —— 跟随主操作);
  内容区动作色为亮蓝 **#3178fc**(hover **#5C97FD**)—— 主按钮 /
  链接 / 文字选中 / 侧栏选中;侧栏选中 =「蓝色左条 + 浅蓝底 #EBF2FF」。
  修订 5-11 的青色系(#0E7C86/#14A3B5/#E6F6F7)已退场。红色仅用于错误语义
  (登录失败提示、删除操作),**永不做主色**;无紫色渐变、无花哨底纹,
  仅登录页用深蓝氛围 + 极淡"影像格网"纹理营造仪式感。
- **字体**:MiSans 400/500/700 唯一品牌字体,18px 基准(16/18/20 体系),
  层级靠字重(500/700)区分;控件高度取 AntD large(40px)以匹配 18px 基准。
  头部产品标题按 OPS 事实 26px/600(MiSans 自托管仅 400/500/700,600 由浏览器落到 700)。
- **布局(OPS 三区壳层)**:68px 图标导轨(`--mainnav-width:68px`,bg **`#0B2540`** 修订 11
  近黑科技蓝,顶部 logo 块与头部同高 60px,一级菜单 = 26px 图标 + 下方 ~16px 短标签、
  68px 内截断;选中 `#3178fc` / 悬停 `#12355C`)+ 60px 白色头部(`--header-height:60px`,横跨导轨右侧:
  左产品标题「LumenCollab 影像协作平台」26px/600,右用户区 = 「用户名/所属机构」双行文字 +
  50px 圆头像(首字占位),无 caret,点击弹出基本信息 + plain 大按钮「退出登录」)+
  头部下方 160px 白色二级侧栏(`--sidebar-width:160px`,可折叠 40px)+ `#f3f5f7` 内容区
  (padding **10px**,修订 8)。
  **侧栏渲染激活一级的 children(修订 7)**:二级目录 = 分组标题(16px 灰字 +
  **20px 组图标,保留**;组间 1px 分隔线),三级页面 = 组内平铺项(18px 字号 /
  40px 行高,**纯文字、无图标** —— 修订 9,OPS SidebarItem 条件渲染、菜单数据无 icon);
  一级永不进侧栏;选中态 = 蓝色左条 + 浅蓝底 #EBF2FF(修订 12)。
  **折叠交互(修订 9,OPS 形态)**:展开态 = 24px Fold 图标绝对定位于侧栏右下角
  (right:4px / bottom:0 / padding:5px,无文字);收起态 = 40px 宽竖条
  (Expand 图标 + 竖排文字 18px/600,逐字换行,如「系统管理」),整条可点击展开。
  **侧栏配色(修订 10/12)**:青色底实验(修订 9)经用户裁定不采用,全部页面回退白色;
  选中态 = 蓝色左条 #3178fc + 浅蓝底 **#EBF2FF** 深蓝字(深蓝对浅蓝片满足 WCAG AA)。
  控制台页 = 左机构树 aside + 右工作区;列表页无页头标题块,工具行 = 查询组件(左)+
  CirclePlus 新增钮(右)直接开场;操作列 = 纯图标链接钮(tooltip 出文案,表头右侧刷新图标);
  分页行居中、高 60px(修订 6);写操作 = 模态对话框。

## Token 对照(原型 CSS 变量 ↔ `src/theme/tokens.ts`)

> 原型目录不受 src/ 的 hex 纪律约束,但所有 token 值与 tokens.ts **逐项一致**;
> CSS 变量直接以所镜像的 AntD token 命名,便于回溯。

| AntD token | 值 | 原型 CSS 变量 | tokens.ts 是否含此键 |
|:--|:--|:--|:--|
| `colorPrimary` / `colorInfo` / `colorLink` | `#3178fc` | `--colorPrimary` / `--colorInfo` / `--colorLink` | ✓ 一致(修订 12 回归原值;修订 5-11 曾为青 #0E7C86,协调者同步 tokens.ts) |
| `colorText` | `#333` | `--colorText` | ✓ 一致 |
| `colorTextSecondary` | `#666` | `--colorTextSecondary` | ✓ 一致 |
| `colorTextTertiary` | `#999` | `--colorTextTertiary` | ✓ 一致 |
| `colorBorder` | `#d7dbe2` | `--colorBorder` | ✓ 一致 |
| `colorBgLayout` | `#f3f5f7` | `--colorBgLayout` | ✓ 一致 |
| `fontSize` / `fontSizeSM` / `fontSizeLG` | `18 / 16 / 20` | `--fontSize` / `--fontSizeSM` / `--fontSizeLG` | ✓(fontSize 键;SM/LG 为 OPS 字阶规格) |
| `fontFamily` | `'MiSans','PingFang SC','Microsoft YaHei',sans-serif` | `--fontFamily` | ✓ 一致 |
| `Layout.headerBg` | `#ffffff` | `--Layout-headerBg` | ✓ 一致 |
| `Layout.siderBg` = `Menu.darkItemBg` | `#0f356a` | `--Layout-siderBg` / `--Menu-darkItemBg` | ✓ 一致(深蓝导航 token 镜像;修订 11 后导轨改用 #0B2540,见下) |
| `Menu.darkItemSelectedBg` | `#007bc2` | `--Menu-darkItemSelectedBg` | ✓ 一致(tokens.ts 现值;修订 11/12 后导轨选中改 #3178fc,见下) |
| `Menu.darkItemHoverBg` | `#32aef3` | `--Menu-darkItemHoverBg` | ✓ 一致(tokens.ts 现值;修订 11 后导轨悬停改 #12355C,见下) |
| 修订 11 导轨配色(用户 2026-11-04 选定) | 背景 `#0B2540` / 悬停 `#12355C` / 选中 `#3178fc`(修订 12 跟随主操作) | `--mainnav-background-color` / `--mainnav-item-hover` / `--mainnav-item-active` | 修订 11/12 · 协调者同步 tokens.ts |
| 修订 12 侧栏选中(替换 rev.1 的 `#3c587d` 白字) | `#3178fc` 蓝条 + `#EBF2FF` 浅蓝底 | `--menu-active-bar` / `--menu-active-bg` | 修订 12 选用 |
| OPS `--menu-text-color`(非 AntD token) | `#6f6f6f` | `--menu-text-color` | OPS 事实 · 二级侧栏菜单文字 |
| `Table.headerBg` | `#f0f3fa` | `--Table-headerBg` | ✓ 一致(兼作二级侧栏菜单悬停底) |
| `Table.rowHoverBg` | `#f6f6f6` | `--Table-rowHoverBg`(兼作斑马行,同值) | ✓ 一致 |
| 壳层度量(specs/web-design-system + OPS var.scss) | `68 / 60 / 160 / 40px` | `--mainnav-width` / `--Header-height` / `--Sider-width` / `--hide-sidebar-width` | ✓(OPS 事实 + 规格规定) |
| 导轨图标 / 标签(修订 5,OPS 实测) | `26px` / `~16px` | `.rail-item svg.i / .lb` | OPS 事实 |
| 侧栏菜单项 / 分组标题(修订 7,OPS sidebar.scss) | 项 `18px/40px 行高/图标 18px`;组题 `16px/图标 20px` | `.side-item / .side-group` | OPS 事实 |
| 分页行(修订 6,OPS .hpage-pagination) | 水平居中 · 高 `60px` | `.pager` | OPS 事实 |
| 内容区 padding(修订 8 用户指定) | `10px` | `.main` | 修订 8 |

原型额外使用、但 **tokens.ts 未手写** 的值(实现时由 AntD 从 `colorPrimary` 派生或取默认):

| 值 | 原型 CSS 变量 | 说明 |
|:--|:--|:--|
| `#003676` | `--navDeep` | 规格调色板的"导航深蓝"一员(web-design-system 规格文本),仅作登录/导览页背景渐变端,不进 tokens.ts |
| `#5C97FD` | `--colorPrimaryHover` | 亮蓝操作悬停(修订 12 指定;历史:AntD 派生 #4f95ff → 修订 5-11 青 #14A3B5) |
| `rgba(49,120,252,.08)` | `--colorPrimaryBg` | 蓝色浅底(树选中行 / 环境标签 / 提示条 / 侧栏选中底),修订 12(AntD 派生) |
| `#ff4d4f` / `#52c41a` | `--colorError` / `--colorSuccess` | AntD 默认错误 / 成功色;错误色仅语义用 |
| `40px` / `6px` / `8px` | `--controlHeightLG` / `--borderRadius(LG)` | AntD large 控件高、默认圆角 |

## 页面清单与 scenario 映射

| 文件 | 内容 | 对应 spec capability(scenario 级) |
|:--|:--|:--|
| `index.html` | 导览:方向、调色板、字阶、度量、token 对照、映射速览、窄屏说明 | — (入口) |
| `login.html` | 居中卡片 + 产品名(LumenCollab / 影像协作平台)、账号+密码、Enter 提示、loading 态、A 类错误内联(「用户密码错误」,即 mock `A0210` msg 原文) | `web-auth-flow` · Login Page:Successful login / Wrong password / Enter submits;D7 明确裁剪隐私协议勾选与忘记密码 |
| `console-shell.html` | 三区壳层:68px 深蓝图标导轨(一级:系统管理 + 三科室)+ 60px 白色头部(产品标题 + 用户区[用户名/机构双行 + 50px 头像])+ 160px 白色二级侧栏(三分组:组织架构/用户权限/系统配置 + 平铺项,可折叠 40px)+ 用户管理(工具行[科室下拉 + 查询 \| CirclePlus 新建用户] + 表格[用户名/姓名/角色/启用开关/创建时间 + 图标操作列] + 居中分页 + 新建用户弹窗,弹窗内注明重名 A 类 msg 落点;无页头标题块) | `web-auth-flow` · Dynamic Navigation(Menus follow /me)、Logout;`web-system-console` · User Management:Paged user list / Duplicate username feedback / Password and enable actions(启用开关列 + 操作列图标) |
| `org-deps.html` | 左机构树(示例医院根,悬停出「新建下级」;底部「新建根机构」虚线钮示意多根能力)+ 右科室表(radiology/us/endo)+ 新建科室弹窗 | `web-system-console` · Org Tree Management:Tree renders with seeded root(多根:第二根可创建)、Org CRUD round-trip(树节点操作入口示意);Department Management:Departments of a selected org / Department CRUD round-trip |
| `role-menus.html` | 角色表(系统管理员 · type 10 · 备注 seed 原文;一行示意角色;工具行[角色名 + 查询 \| CirclePlus 新建角色])+「菜单授权」弹窗:可勾选三级树(系统管理 → 组织架构/用户权限/系统配置 → 页面;放射科[+按钮型「报告审核」];超声科;内镜科)、青色说明条(全量替换保存 / 重开显示已授权集)、保存授权按钮 | `web-system-console` · Role Management and Menu Assignment:Role CRUD round-trip(图标编辑/删除)、Menu assignment is full-replace |
| `dicts-logs.html` | 一页双栏:左 = 字典目录(工具行[编码/名称查询 \| CirclePlus 新建目录];common_status/通用状态)+ 明细(工具行[目录选择 + 查询 \| CirclePlus 新建明细];启用/停用按 sort 升序,图标编辑/删除);右 = 操作日志(类型/操作人/月份过滤 + 只读表,无新增钮,只读语义注于表下注) | `web-system-console` · Dictionary Management:Catalog list and duplicate feedback / Details ordered by sort;Operation Log Query:Filtered log query / Read-only surface |
| 全部控制台页 | shell 度量、MiSans、18px 基准、OPS 深蓝 | `web-design-system` 四条 requirement 全覆盖(MiSans Font Bundling / OPS Type Scale / OPS Palette Tokens / OPS Layout Metrics) |

**菜单树与壳层一致性(修订 3 + 修订 7)**:一级菜单只在 68px 导轨(系统管理 + 三科室,
永不进侧栏);侧栏渲染激活一级的 children。修订 7 菜单结构决策:系统管理下引入三个二级
分组目录 —— **组织架构**(机构管理、科室管理)、**用户权限**(用户管理、角色管理、
菜单管理)、**系统配置**(字典管理、日志查询);侧栏 = 分组标题(16px + 20px 图标)+
组内平铺项(18px / 40px 行高 / 18px 图标),组间 1px 分隔线;科室类一级为单页直达
(侧栏单个平铺项)。⚠ 该结构为决策目标:实现前需同步 mock `SEED_MENUS` 与后端 V2 种子
(后端跟进项,与 roles/list 并列)。未单独出原型的「菜单管理」平铺项置灰并注明替代观察点;
导轨科室项置灰为占位(后续批次)。

## 数据真值(对照 `lumen-web/mock/_fixtures.ts`)

- 用户:admin / 系统管理员 / 启用 / 2026-11-04 09:00:00(种子唯一行;表格其余 3 行标注「示意」)。
- 角色:系统管理员,type 10,备注 `seed default administrator role`(第二行示意)。
- 机构:示例医院(根);科室:放射科 `radiology`、超声科 `us`、内镜科 `endo`。
- 字典:目录 `common_status`/通用状态;明细 启用(1) / 停用(0),sort 1/2。
- 日志:mock 演示 3 行(重置用户密码 / 角色-菜单全量分配 / 创建科室),倒序展示(后端日志表种子为空,mock-only)。
- 登录错误文案:`用户密码错误` = `auth.mock.ts` 的 `fail('A0210','用户密码错误')` 原文。
- 头部用户区「所属机构」显示种子机构「示例医院」;profile 载荷暂无 org 字段 —— 后端需在
  /me 或 profile 中补充 org 字段(后端跟进项)。

## 窄屏策略(仅说明,不要求响应式实现)

1440×900 为主视口。窄屏(≥1024)时:二级侧栏折叠为 40px 图标栏(「收起」钮,原型可演示),
主导航轨 68px 不变;表格容器横向滚动、表头随动;工具行换行堆叠;dicts-logs 双栏退化为
上下堆叠;登录卡片宽度自适应收窄。本批原型不实现整体响应式,实现批次(5.2+/6.x)按此说明落地。

## 请确认清单(逐页)

**总方向**
- [x] 导轨背景色已由用户选定**近黑科技蓝 #0B2540**(修订 11,2026-11-04):悬停 #12355C、选中跟随主操作 **#3178fc**(修订 12);协调者同步 tokens.ts 与规格。
- [x] 主操作色已由用户裁定**回归亮蓝 #3178fc**(修订 12,2026-11-04):hover **#5C97FD**、侧栏选中片 **#EBF2FF**;青色系退场,单蓝色系(近黑蓝壳 + 亮蓝操作);协调者同步 tokens.ts 与规格。
- [ ] 近黑蓝壳 + 亮蓝操作(修订 12 单蓝色系,#3178fc / hover #5C97FD)+ MiSans + 18px 基准的整体观感是否符合预期?
- [ ] **三区壳层结构**(68px 导轨[26px 图标 / 16px 标签,均纯白 #fff] + 60px 白色头部 + 160px 白色二级侧栏)是否符合 OPS 预期?
- [ ] **侧栏三级结构(修订 7 分组)**:组织架构 / 用户权限 / 系统配置 三个分组标题(**保留 20px 图标**)+ 组内平铺项(**修订 9:纯文字无图标**,18px-40px 行高)、组间 1px 分隔线是否 OK?该结构需后端 V2 种子与 mock 同步(确认后列为跟进项)。
- [ ] **折叠交互(修订 9,OPS 形态)**:右下角 24px Fold 图标收起 → 40px 竖条(Expand 图标 + 竖排「系统管理」18px/600)展开 — 是否 OK?
- [x] 青色侧栏实验(修订 9)已由用户裁定**回退白色**(修订 10,2026-11-04);选中态现 = 蓝色左条 + #EBF2FF 浅蓝底(修订 12),四页统一。
- [ ] 已按 OPS 移除列表页页头标题块(h1),页面身份 = 侧栏选中项,是否 OK?
- [ ] **工具行构成**(左 = 查询组件,右 = CirclePlus 新增钮)与**图标操作列**(纯图标 + tooltip,表头右侧刷新)是否 OK?
- [ ] **分页行居中(高 60px,修订 6)**与**内容区 padding 10px(修订 8)**是否 OK?
- [ ] 登录页的"仪式感"程度(深蓝氛围、格网纹理、品牌区)是否合适,还是要更素?
- [ ] 表格密度(表头 11px / 表体 13px 行距节奏、18px 表体 / 16px 表头)是否合适?

**login.html**
- [ ] 无隐私协议勾选、无忘记密码(规格裁剪)— 确认页面上确实没有。
- [ ] A 类错误以 `message.error` 顶部浮层呈现(位置如图示),而不是卡片内红字 — 认可吗?
- [ ] Enter 提交提示放在密码框下方右侧,是否保留?

**console-shell.html**
- [ ] 头部构成:产品标题(26px/600)+ 「开发环境」占位标签 + 用户区(用户名/机构双行 + 50px 头像右侧,悬停弹出 plain 退出登录按钮);无 caret、无面包屑、无 OPS 的问题反馈/客服/聆听图标 — 认可吗?
- [ ] 用户区「所属机构」暂显示种子「示例医院」(profile 载荷无 org 字段,已列后端跟进)— 值来源确认。
- [ ] 导轨选中 `#3178fc` / 悬停 `#12355C`;二级侧栏悬停浅底(`#f0f3fa`)、选中蓝色左条 + 浅蓝底 #EBF2FF(修订 12);未覆盖菜单项置灰处理的接受度。
- [ ] 侧栏「收起」钮折叠到 40px 图标栏的示意是否 OK。
- [ ] 用户表列(用户名/姓名/角色/启用开关/创建时间 + 操作列[编辑 / 重置密码 / 删除 图标])- 重置密码以钥匙图标并入操作列,是否 OK?
- [ ] 新建用户弹窗字段与"重名 msg 显示于表单、弹窗不关"的说明位置。
- [ ] 示意行的「示意」角标标注方式是否足够清晰。

**org-deps.html**
- [ ] 左树右列表的分区比例(250px 树 / 自适应表);「新建根机构」虚线钮表达多根能力是否直观。
- [ ] 机构上下文由左树选中态承载(信息 badge 已按修订 3 删除);工具行 = 编码/名称查询 + CirclePlus 新建科室。
- [ ] 新建科室弹窗:科室编码必填 + 编码用途说明(radiology/us/endo)。

**role-menus.html**
- [ ] 授权弹窗中"全量替换 / 重开显示已授权集"青色说明条的文案与位置。
- [ ] 授权树已同步三级结构(系统管理 → 三分组 → 页面;放射科 → 报告审核)- 与修订 7 侧栏结构一致。
- [ ] 角色表"类型"列直接显示种子原值 10(未杜撰语义)— 接受还是需要映射文案?

**dicts-logs.html**
- [ ] 一页双栏仅为原型展示(实现为两个独立页面)— 确认这种展示方式便于对比确认。
- [ ] 字典明细以"目录下方明细表"示意(实现时为抽屉/表二选一,待 6.6 定)- 倾向哪种?
- [ ] 各面板工具行 = 查询组件 + CirclePlus 新增钮;日志只读语义移至表下注;明细「目录选择」下拉是否 OK。

## 已知边界(原型未覆盖)

- **修订 7 的三分组菜单结构为决策目标**:实现前需同步 mock `SEED_MENUS` 夹具与后端 V2 种子
  (后端跟进项,与 roles/list 并列);原型四页与授权树已按该结构呈现。
- 菜单管理只读页(含 perms 展示)未单独出原型;树形态见角色授权弹窗。
- 机构 CRUD 的编辑/删除弹窗未单独出原型;树节点悬停「+」仅示意入口。
- 退出登录后的 203 过期流程、守卫重定向属逻辑行为,静态原型不表达(见 5.3)。
- 「报告审核」按钮型节点的按钮级 gating 示意不在本批(见 5.4 perms-gated helper)。
- 登录页种子账号提示(admin / admin123)仅开发 mock 环境,真实环境无此文案。
