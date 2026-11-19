# Tasks: rep-temp-normal-range-sev

> TDD：先红后绿；受影响测试随批跑，verify 阶段跑 lumen-system 全量。
> 构建一律 `cd lumen-sev && export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home && ./mvnw ...`（reactor 聚合构建）。

## 1. V47 一次性迁移

- [x] 1.1 红：迁移测试——fixture SQL 造 v1 行（normal 覆盖 `19-40`/`<5`/`≤5`/`>3`/`阴性`/null/带空格变体），断言迁移后 v2 形态逐例正确（含「阴性」text 原文逐字节保留）；断言重放幂等（二次执行 0 行变化、无错）
- [x] 1.2 绿：`V47__rep_temp_normal_range_v2.sql`——单 UPDATE + LATERAL jsonb 重建（WITH ORDINALITY 保序），CASE+正则按 design D3 规则集，守卫 `content->>'version' = '1'`（SRF 入参 null/scalar 安全化——lateral 求值先于 ON 过滤）
- [x] 1.3 新装机 no-op 断言（V39 空列表 → 迁移 0 行成功）；重跑 `./mvnw -pl lumen-system -am test -q` 种子/迁移组绿

## 2. DTO v2 + 服务端校验

- [x] 2.1 红：DTO/服务层测试——合法 range（双边/单边/小数）与 text（≤64）存储 verbatim；min>max 拒 A0400 命名字段；range 两端全空拒；text 空白/超 64 拒；kind 非法值拒；version=1 或字符串 normal（v1 形态）拒（A0400/A0427 两层：服务层版本钉 2 拒 version≠2；字符串 normal 由 Jackson 绑定层拒）
- [x] 2.2 绿：新增 `RepTempMeasureNormal` 嵌套 DTO（kind + min/max BigDecimal + text，Lombok 四件套）；`RepTempMeasure.normal` 类型改 union；服务层校验按 design D2（A0400 命名字段）；写路径钉 version 2
- [x] 2.3 目录拒 content、sections 非空等既有断言回归不动

## 3. 夹具与回归

- [x] 3.1 `RepTempContentApiTests` 等既有 content 夹具 normal 升 union 形态（AO normal 升 range union 断言；wrongVersion 改 version 3——v2 下 2 已合法）；web 类型注释对齐（version pin 2 由 web change 落）
- [x] 3.2 lumen-system 全量 `./mvnw -pl lumen-system -am test -q` 绿（共享测试库：独占窗口 + rerunFailingTestsCount 容错惯例）——全量一轮 setting/migration/content 全绿；AuthDeviceApiTests/AuthLoginTests 3 例共享库并行夹具互删 flake，隔离重跑 24/24 绿（非本次回归）
