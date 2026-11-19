# Tasks

## 1. 后端:V42 种子 + 服务层族守卫(lumen-sev / lumen-system setting 域)

- [x] 1.1 `V42__rep_temp_public_perms.sql`(V28 纪律,V41 被并行会话占用顺延):4 个 F 节点(公共模板可见/新建公共模板/编辑公共模板/删除公共模板,`setting:temp:public:{list,create,update,delete}`,sort 4–7,up_id=报告模板 C 行)NOT EXISTS 守卫 + role 1 守护授权 + setval 尾
- [x] 1.2 `RepTempServiceImpl.requireScopeFamily(type, op)` 助手(SecurityContextHolder authorities 直读,缺族抛 AccessDeniedException 命名缺失权限串)+ 接线:getTree/getPage type=1 断言 public:list、create 按 request.type、update/delete 按 requireOwnedRow 行 type;TDD 先行(perm 矩阵:仅基本族公共写/读 → A0301,仅公共族公共写成功且 owner_uid NULL,双族全通,非 owner 私行仍 A0400)
- [x] 1.3 `RepTempController` 三个写端点静态钉放宽 `hasAnyAuthority(基本族, 公共族)`(类门控不动);TDD:仅公共族用户过静态钉
- [x] 1.4 seed 测试:SettingSchemaSeedTests 补菜单断言(若其结构覆盖 F 行计数/权限位——注意该文件可能仍在他会话脏集,冲突时最小行数原则)或独立 V42 断言测试

## 2. 前端:Segmented 显隐 + 动作键控(lumen-web)

- [x] 2.1 `tempPerm(op)` 键控助手 + Segmented options 按 `hasPerm('setting:temp:public:list')` 过滤 + 初始 scope 惰性化(无公共权限默认个人);TDD 先行(三场景:隐藏/默认翻转/键控显隐)
- [x] 2.2 全部动作 hasPerm 换 tempPerm(新建目录/新建子目录/编辑目录/删除节点/添加模板/表格编辑与删除);权限 fixtures 补 `setting:temp:public:*` 变体,存量 26 用例回归绿(双族用户行为不变)
- [x] 2.3 个人 scope 无公共权限用户的端到端形态:Segmented 单选项、无公共树/表请求、动作键控基本族(对照 delta 场景「公共 option hidden」)

## 3. 验证与收尾

- [x] 3.1 后端 reactor 全量(共享测试库窗口纪律)+ 前端 vitest 全量
- [x] 3.2 WHEN/THEN 对照:exam-setting delta 8 场景 + web-system-console delta 9 场景 + system-data delta 1 场景逐条核验,verify.md 落 L1/L2/L3
