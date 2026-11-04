# Batch 6a 续作 Brief — web-foundation-auth-console(接续中断的 6a)

> 背景:前任代理完成实现后死于 API 配额(冒烟阶段中途),工作区留有**未提交的实现**
> (三页 + org-tree/console-shell 共享组件 + 4 个测试文件,当前 127/127 全绿)。
> 你的职责 = **审计补齐 + 干净冒烟 + 报告**,不推倒重来。
> 原 brief:`openspec/sdd/reports/web-foundation-b6a-brief.md`(验收标准不变)。

## 步骤

1. **审计**(先读后判):
   - 对照原 brief 的 TDD 清单逐条核对三页测试覆盖(6.1 CRUD/多根/删除确认;6.2 按 org
     过滤/CRUD/重码不关弹窗;6.3 列表+客户端分页/新建成功与重名/密码重置回显/enable 切换)。
     缺哪条补哪条(缺测试先写 RED 再补实现,维持 TDD 证据链)。
   - 检查前任最后的自述遗留:「分页 DOM 选择器写错」「mock 状态跨运行被探针污染」——
     确认测试代码里是否仍有错误选择器;如有,修正并说明。
   - 纪律复核:hex 守卫绿、无内联 URL 拼接、无新依赖、未碰壳层/路由/mock/规格。
2. **干净冒烟**(端口 5179,`--strictPort`):
   - 每轮场景前**重启 dev server**(避免 mock 内存态跨场景污染——前任的死因之一);
     探针类请求(探测分页 DOM 等)与断言类请求分开,报告注明方法。
   - 场景:6.1 orgs 多根创建/子机构 CRUD;6.2 deps 选中机构过滤/新建重码反馈;
     6.3 users 列表分页(用正确的 AntD 分页选择器)/新建重名反馈/重置密码回显/enable 切换。
     测完 kill,确认端口释放。
3. **报告**:写 `openspec/sdd/reports/web-foundation-b6a-report.md`(中文)——注明「续作
   (前任 API 配额中断)」;含审计结论(覆盖矩阵:brief 条目 × 测试名)、修正项、冒烟证据、
   布局取自原型的区块说明、权限门控用法、遗留问题。

## 铁律(不变)
- 不 commit;不动壳层/登录/路由/mock/规格/并行会话文件;不放宽 strict;失败即停如实记录。
- 全程 `pnpm --dir lumen-web`;hex 守卫必须常绿。
