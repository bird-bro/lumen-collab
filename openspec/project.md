# Project Overview - lumen-collab

## System
lumen-collab (brand: LumenCollab) - open-source medical imaging collaboration platform for exam-department workflow, pairing with the open-source PACS **dcm4chee-arc-light**. v1 delivers a RIS (radiology); ultrasound / endoscopy follow as independent microservices, all sharing one exam-workflow kernel (booking → registration → exam → report). License: MIT.

## Tech Stack
| Layer | Tech |
|:--|:--|
| Backend | Java 17 + Spring Boot 3.x + Spring Cloud Alibaba (Nacos, Sentinel) + Spring Cloud Gateway + OpenFeign |
| Frontend | React 18 + TypeScript + Vite + Ant Design 5 (pnpm, single SPA, lazy-loaded department routes) |
| Database | PostgreSQL (one DB per service) + Redis + RocketMQ |
| ORM / Migration | MyBatis-Plus + Flyway (per-service `db/migration`) |
| Integration | dcm4che (MWL / MPPS SCP) + HAPI HL7v2 (not HAPI FHIR) + dcm4chee-arc QIDO-RS |
| Deploy | Docker Compose (incl. dcm4chee-arc-light) |

## Module Map
```
lumen-collab/                         monorepo root (harness + frontend + deploy)
├── lumen-sev/                        ★ backend Maven aggregator (pom.xml here)
│   ├── lumen-bom/                    dependency versions (single source)
│   ├── lumen-common/                 shared libs (jar): core / web / mybatis / security / dicom
│   ├── lumen-exam-core/              ★ exam workflow kernel: booking→registration→exam→report state machine
│   ├── lumen-api/                    inter-service contracts: patient-api / integration-api (Feign + DTO)
│   ├── lumen-gateway/                Spring Cloud Gateway
│   ├── lumen-auth/                   users, roles, departments, permissions (JWT)
│   ├── lumen-patient/                patient master index (EMPI), shared across departments
│   ├── lumen-ris/                    radiology business (v1 ★) - depends on lumen-exam-core
│   ├── lumen-us/                     ultrasound (v2) - depends on lumen-exam-core
│   ├── lumen-endo/                   endoscopy (v2) - depends on lumen-exam-core
│   └── lumen-integration/            DICOM MWL/MPPS SCP + HL7v2, routes by department
├── lumen-web/                        React SPA (frontend stack dir)
└── docker/                           docker-compose incl. dcm4chee-arc-light
```

## Architecture
- **Service split**: platform services (gateway / auth / patient / integration) + department services (ris, later us / endo). Splitting by department provides **fault isolation**; HA additionally needs multi-instance + redundancy (gateway / Nacos / PG).
- **New department = thin service** on `lumen-exam-core`, extending only department differences (exam items, report templates, device wiring).
- **Communication**: sync query = OpenFeign + Sentinel fallback; state change = RocketMQ event (transactional message = atomic local-DB write + event publish). No cross-service distributed transactions.
- **DB per service** (`lumen_auth` / `lumen_patient` / `lumen_ris` / `lumen_us` ...): no cross-DB queries - services talk via API/events only.
- **DICOM/HL7 convergence**: modality devices see only `lumen-integration` (one MWL/MPPS address); department services stay pure business. HIS orders via HL7v2 ORM^O01; manual order entry as fallback.
- **Study linkage**: QIDO-RS by Accession Number attaches dcm4chee Studies to exam records.
- **Flexible data**: report templates, structured findings, department forms → PostgreSQL `jsonb` + GIN index.

> No coding conventions here - those live in `.claude/rules/` and per-stack `CLAUDE.md`.
