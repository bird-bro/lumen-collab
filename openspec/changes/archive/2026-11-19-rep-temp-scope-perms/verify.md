<!--
  verify.md (rep-temp-scope-perms). overall=PENDING: the web L3 rerun is gated on the
  parallel session landing its in-flight lumen-web sources (console-shell/dict-items/
  types) — 2 full-run failures in device/exam-body tests are attributed to that WIP
  (files untouched by BOTH sessions this cycle; isolated rerun 26/26 green). The final
  overall lands after the rerun; the archive gate must refuse PENDING until then.
-->
---
verify:
  change: rep-temp-scope-perms
  date: 2026-11-19
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "9/9"
      L3: PASS
    lumen-web:
      L1: PASS
      L2: "9/9"
      L3: PASS
    system-data:
      L2: "1/1"
---

# Verification: rep-temp-scope-perms

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS (full reactor compiled within `./mvnw test -q`) | 9/9 | PASS (full reactor: setting/patient suites green; manage-domain scattered failures = the established shared-server transient pattern — batch-1 implementer re-verified 8 manage classes 68/68 isolated ON THE CURRENT TREE after V42+guards landed; D5 additions class exit 0) | logs/test-backend-v2.log |
| lumen-web | PASS (`pnpm build` + `tsc --noEmit` exit 0, final tree) | 9/9 | PASS (full suite **418/418 green** after the parallel session landed its lumen-web pass; the interim 2 failures in device/exam-body were its mid-flight WIP — files untouched by this change, isolated 26/26 green at the time; rep-temp page file 30/30 green throughout, incl. inside the polluted run) | logs/test-lumen-web-v3.log / logs/build-lumen-web.log / logs/iso-device-exambody.log |
| system-data (seed) | — | 1/1 | covered by AuthRepTempScopePermTests (seed shape + re-execution no-op) | logs/test-backend-v2.log |

## Attribution Notes

- **manage 域瞬时态(第 3 次出现)**:全量 reactor 的 manage 失败(OrgLogoUpload 7E / DeviceRoomSeed 1F / OperationLog 1F / AuditLogWrite 1E)与昨夜同款;隔离复验 68/68 绿由批次 1 implementer 在 V42+守卫落地后的当前树完成。非本 change(本 change 只动 setting 域 rep-temp 面与 V42 种子)。
- **web 两个失败(已消解)**:device/exam-body 的页面与测试文件本周期无人改动(不在任何脏集),隔离 26/26 绿;污染源 = 并行会话在途的 console-shell/dict-items/types 编辑。并行会话落地后全量重跑 418/418 全绿(最终树 L1 亦 exit 0)。
- **D5 补格**(0b4e382,batch1 review Minor 1):仅公共族 owner 写自己私行仍 A0301 + 双 list 只读用户双 scope 正向隔离。

## Scenario Alignment

### specs/exam-setting/spec.md delta (9 scenarios)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Tree returns both scopes independently | IMPLEMENTED (regression) | AuthRepTempApiTests 全程绿 |
| Template page under a folder | IMPLEMENTED (regression) | AuthRepTempApiTests |
| Template payloads carry the content object | IMPLEMENTED (regression) | AuthRepTempContentApiTests 7/7 |
| Static pins admit either family | IMPLEMENTED | AuthRepTempScopePermTests.publicWriteWithPublicFamilySucceedsAndStaysOwnerless(publicCreator 无基本写位过钉) |
| Non-owner private write refused | IMPLEMENTED | nonOwnerPrivateWriteStillRefusedA0400BeforeFamily(public 族 stranger,A0400 先于族) |
| Private create binds the owner | IMPLEMENTED | dualFamilyUserKeepsEveryPathGreen(private create owner_uid=RUN_UID) |
| Public write without the public family refused | IMPLEMENTED | publicWritesWithoutPublicFamilyRefuseContentFree(create/update/delete 三路 A0301 + 无插入 + content-free) |
| Public write with the public family succeeds | IMPLEMENTED | publicWriteWithPublicFamilySucceedsAndStaysOwnerless(create/update/delete 200,owner_uid NULL) |
| Public read without public:list refused | IMPLEMENTED | publicReadWithoutPublicListRefused(tree/page A0301 content-free;base list 仍读私树) |
| D5 owner-variant + read-only isolation *(verify 补格,batch1 review M1)* | IMPLEMENTED | publicFamilyOnlyCannotWriteItsOwnPrivateRow + dualListReadOnlyUserIsolatesBothScopes |

### specs/web-system-console/spec.md delta (9 scenarios)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Tab toggle switches scope without cross-leak | IMPLEMENTED (regression,双族 fixtures) | rep-temp-page.test.tsx |
| Directory node creates a child directory | IMPLEMENTED (regression) | 新建子目录 prebind 用例 |
| Section mode edits sections and measures | IMPLEMENTED (regression) | 分节编辑用例 |
| Mode switch converts prose to sections | IMPLEMENTED (regression) | 转换映射用例 |
| Structured to prose conversion refused | IMPLEMENTED (regression) | 拒绝用例 |
| Structured row renders summary cells | IMPLEMENTED (regression) | 摘要用例 |
| 公共 option hidden without the public list perm | IMPLEMENTED | 新用例:Segmented 单选项/默认个人/零 type=1 请求 |
| 公共 scope actions consult the public family | IMPLEMENTED | 新用例:list 持有无 create/update → 读可写隐 |
| 个人 scope actions keep the base family | IMPLEMENTED | 新用例:基本族原样键控 |

### specs/system-data/spec.md delta (1 scenario)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Admin gains the public family without manual grant | IMPLEMENTED | publicFamilyNodesSeededUnderRepTempPageAndAdminAutoGranted(4 F 节点 sort/挂载/role 1 授权逐项断言)+ v42ReExecutionIsALiveRowNoOp(重放幂等) |
