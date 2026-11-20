# setting-dict-1024-doc-sweep-sev — setting 域文档扫：dict 1027 残留对齐 1024

**Date**: 2026-11-20 · **Status**: implemented-pending-archive · **Stack**: lumen-sev（单栈，无 spec delta）

## Why

设备与检查室分表模型裁决（2026-11-10/11-12）欠账的轻项（memory
device-room-separate-tables-model「exam-setting(V28) 按名查『检查类型』待改『设备类型』」的
落地形态）：V27 统一后唯一模态目录是 **1024「设备类型」**（1021/1023/1027 全退役），
modality-dict-id-anchor-sev 已把运行时锚到 1024 并同步了 spec——但 setting 域
**25 个 Java 文件的 Swagger 注解 / javadoc / 测试断言消息仍写「dict 1027 value」**：
API 文档（/v3/api-docs）在向消费者撒谎（1027 不存在），后来者 grep 1027 会被误导。

## What Changes

- 21 个 src/main 文件 + 4 个测试文件：`dict 1027` → `dict 1024`
  （DTO `@ApiModelProperty`、entity javadoc、3 个 service 接口 javadoc、
  4 个 Auth*ApiTests 的断言消息文案）；
- **零运行时变更**：注解/注释/消息文案级，不触任何签名、SQL、种子；
- 刻意保留：SettingIntegrationTestBase / DictModalityMapperTests 里的历史叙事
  （「1021/1023/1027 all vanished」「spec-false 1027 catalog」——守卫理由的史录，
  与 V27/V28 注释同性质）；**V27/V28 迁移注释不动**（已应用迁移，checksum 保护）；
- manage 域用户可见性 CSV 的「dict 检查类型 codes」字样不在本扫范围
  （users 页例外是既有裁决，另行处理）。

**spec delta**：无——spec 已被 modality-dict-id-anchor-sev 锚到 1024（specs 内
1027 零命中），归档走 --skip-specs。

## Scope

- `lumen-sev`: `lumen-system/src/{main,test}/java/org/birdbro/lumen/setting/**`
  （25 文件，机械单串替换）

## Impact

- 无 API 契约变更（注解字符串不在契约内）、无库变更、无迁移。
