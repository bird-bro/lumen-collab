# Tasks

## 1. 解析源切换

- [x] 1.1 `src/utils/dict-items.ts` 新增 `MODALITY_DICT_ID = 1024`（含裁决注释）；verify: `pnpm build` 类型通过（build exit=0）
- [x] 1.2 exam-item/exam-body/exam-method/rep-template 四页 `dictItemsByName(DICT_MODALITY)` → `dictItemsById(MODALITY_DICT_ID)`，移除失效名字常量；verify: grep 无 `'检查类型'` 目录名字符串残留于运行时代码（页面零残留，util 自身定义留给 roles/audit）+ 真后端行为由 verify L3 验证

## 2. 单测 mock 同步

- [x] 2.1 exam-item/exam-body/exam-method/rep-temp 四个页面测试的 dict mock 从 by-name 改 `dictItemsById(1024)`，断言同步；verify: 四套件绿（RED 73 失败 → GREEN 73/73，logs/test-mock-red.log → test-mock-green.log）

## 3. 集成验证

- [x] 3.1 `pnpm test` 全量相关套件 + `pnpm build`，日志重定向文件；verify: exit 0，路径记录（build exit=0 logs/build-web.log；全量 432/433——1 失败为 exam-item Popconfirm 用例 jsdom 计时 flake，定向跑 73/73 + 隔离重跑 ×2 全绿定性非回归，logs/test-full.log）

## Workflow follow-up

- 双栈 verify PASS 后与 `modality-dict-id-anchor-sev` 批量归档（跨栈 gate）。
