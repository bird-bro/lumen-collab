# Spec Delta: exam-setting

## Purpose

Drop the unused 常用项目 page filter from the exam-item endpoints while keeping the
entity-level is_common capability (column, write paths, drawer switch) untouched.

## MODIFIED Requirements

### Requirement: Exam Item Endpoints

The service SHALL expose `/api/v1/setting/exam-items` with `GET /page` (filters:
modality, groupId, keyword over name/code; paged), `GET /{id}`, `POST` create,
`PUT /{id}` full update, `DELETE /{id}` logical delete — each gated by perms
`setting:item:{list,create,update,delete}`. The page query carries NO 常用项目
filter; the item's `is_common` fact remains create/update writable for the
billing-page 常用清单 marker.

#### Scenario: Page filter by group

- **WHEN** `GET /page?modality=CT&groupId=5` is requested
- **THEN** the response lists exactly the non-deleted CT items whose body belongs to
  group 5, with body and method display names resolved server-side

#### Scenario: Perm-gated write

- **WHEN** a create request arrives without `setting:item:create`
- **THEN** the service refuses it per the platform auth contract
