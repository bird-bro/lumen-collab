# Tasks — exam-item-common-filter-drop

## 1. Removal

- [x] 1.1 `ItemPageQuery`: drop the `isCommon` field + toggle mention in the class
      javadoc
- [x] 1.2 `ExamItemServiceImpl.getPage`: drop the 常用项目 filter branch
- [x] 1.3 `ExamItemService` interface + `ExamItemController`: drop the isCommon
      mention from the page-method javadoc / prose

## 2. Verification

- [x] 2.1 `grep isCommon` over the query path returns only the write-side (entity,
      create/update DTOs, tests' payloads) references
- [x] 2.2 `./mvnw test -q -pl lumen-system -am` green (246 tests / 49 classes, exit=0,
      log `/tmp/exam-item-filter-drop-test.log`; JDK 11 export per repo memory; one
      ORPHANED surefire report `TEST-org.birdbro.lumen.system.AuthSignLogQueryTests.xml`
      — class deleted long ago, 08:57 stale failures — removed from target)
