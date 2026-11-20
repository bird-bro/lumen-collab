# Tasks — setting-dict-1024-doc-sweep-sev

## 1. Sweep

- [x] 1.1 25 个 Java 文件 `dict 1027` → `dict 1024`（21 main + 4 test 断言消息）
- [x] 1.2 grep 验证：src Java 内 1027 仅剩 3 处刻意保留的历史叙事；迁移 .sql 零触碰

## 2. Verification

- [x] 2.1 全 reactor compile exit=0（/tmp/dict-sweep-compile.log）
- [x] 2.2 lumen-system -am 测试绿：319 tests / 0 failures / 0 errors
      （/tmp/dict-sweep-test.log，exit=0；surefire 聚合核对）

## 3. Spec

- [x] 3.1 无 spec delta（specs 内 1027 零命中，modality-dict-id-anchor-sev 已锚定）——
      归档走 --skip-specs
