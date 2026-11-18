# Tasks — exam-item-external-code-sev

## 1. Tests first

- [x] 1.1 `AuthExamItemApiTests`: create with `externalCode` echoes in detail;
      create without → NULL echo; update replaces the mapping code
- [x] 1.2 `AuthExamItemApiTests`: two live items in the same modality with the
      SAME `externalCode` both persist (打包计费 — no uniqueness rejection);
      page `keyword` over the mapping code lists the item
- [x] 1.3 `SettingSchemaSeedTests`: V37 applies — `exam_item.external_code`
      varchar(32) nullable exists, no unique index on it

## 2. Implementation

- [x] 2.1 `V37__exam_item_external_code.sql`: single `ALTER TABLE exam_item ADD
      COLUMN external_code varchar(32);` (additive, no index)
- [x] 2.2 `ExamItem` entity + `ItemCreateRequest` / `ItemUpdateRequest` / `ItemVO`:
      `externalCode` field + javadoc（对照编码 — 外部系统对照锚点，HIS 首个消费方）
- [x] 2.3 `ExamItemServiceImpl`: keyword LIKE branch adds
      `.or().like(ExamItem::getExternalCode, query.getKeyword())`

## 3. Verification

- [x] 3.1 `./mvnw -pl lumen-system -am test`（aggregator 根，JDK 11）green —
      ledger in the apply log
- [ ] 3.2 `/opsx:verify` PASS — verify.md written
