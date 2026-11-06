# Tasks: org-status-toggle-api

## 1. Endpoint

- [x] 1.1 Add a validated status request DTO.
- [x] 1.2 Add `OrgService.setStatus` and implement a live-org targeted patch.
- [x] 1.3 Add `PUT /system/orgs/{id}/status` pinned by `system:org:update`.

## 2. Verification

- [x] 2.1 Add an integration test for valid toggling and invalid status rejection.
- [x] 2.2 Run `./mvnw test -q` from `lumen-sev/` with JDK 11.
