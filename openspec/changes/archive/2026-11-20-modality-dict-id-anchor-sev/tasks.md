# Tasks

## 1. Mapper 锚点切换

- [x] 1.1 `DictModalityMapper` @Select SQL `c.name = '检查类型'` → `c.id = 1024`，javadoc 改写为 id 锚定理由（引用 2026-11-19 裁决，删 1021/1027 翻动史论据）；verify: `./mvnw compile -q` 通过
- [x] 1.2 新增 mapper 锚点测试（真实种子库：`countLiveModality('CT') == 1`、未知 token `== 0`，SQL 文本无目录名依赖）；verify: 该测试绿（RED→GREEN: logs/test-mapper-red.log → test-mapper-green.log）

## 2. 受影响套件 + 规格核对

- [x] 2.1 运行 setting 域受影响测试（ExamItem/ExamMethod/ExamBody/RepTemp api tests + AuthDeviceRoomSeedTests），日志重定向文件；verify: exit 0 或失败逐条定性（共享库瞬态参照已知四坑）（44 tests, 0 failures, exit=0, logs/test-affected-suites.log）
- [x] 2.2 主规格同步核对：exam-setting/system-data 两处 MODIFIED 场景为超集且与实现一致；verify: 走查结论记入 verify.md 素材（logs/spec-walkthrough.md；diff 走查 PASS 无 blocker）

## Workflow follow-up

- 双栈 verify PASS 后与 `modality-dict-id-anchor-web` 批量归档（跨栈 gate）。
