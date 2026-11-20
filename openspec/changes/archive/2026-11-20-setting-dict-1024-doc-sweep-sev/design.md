# Design — setting-dict-1024-doc-sweep-sev

## D1 — 机械单串替换，历史叙事保留

`grep -rl "dict 1027" src --include="*.java" | xargs sed 's/dict 1027/dict 1024/g'`
——只动 .java（迁移 .sql 不在匹配面），扫后 grep 验证：Java 内 1027 仅剩 3 处
**历史叙事**（SettingIntegrationTestBase:112,121 与 DictModalityMapperTests:20），
刻意保留——它们解释守卫为何锚 1024，删掉反而丢上下文。

## D2 — 验证方式

纯文档扫无 WHEN/THEN 行为面 → 不新增测试（TDD 不适用）；验证 =
L1 全 reactor compile ✓ + L3 受影响模块（lumen-system -am）测试绿 + grep 零残留。
