# Spec Delta

## ADDED Requirements

### Requirement: City Reference Table
The service SHALL provide the administrative-divisions reference table `public.dict_city`
in the 随访 lineage shape - 13 columns including `pinyin`/`lng`/`lat`/`initial`, no audit
block (static reference data) - seeded by lumen-system's V4 migration from the lineage
database snapshot: 3751 rows (level 0 ×1, level 1 ×35, level 2 ×373, level 3 ×3342). The
`Long` primary key is a recorded lineage-fidelity deviation from the Integer convention.

#### Scenario: Seeded divisions match the lineage checksum
- **WHEN** a fresh database receives the migrations
- **THEN** `public.dict_city` holds 3751 rows - level 1 ×35, level 2 ×373, level 3 ×3342
- **AND** every copied column resolves (13-column shape asserted) and the 中国 root
  (level 0, `up_id` 0) is present as the tree root

### Requirement: City Reference Query
The service SHALL expose READ-ONLY query endpoints under `/api/v1/system/dicts/city/**`:
`GET /tree` (assembled from `up_id` = 0) and `GET /list` filtered by `level`/`upId`. NO
mutation endpoints SHALL exist for the table (user decision 2026-11-03: no frontend CRUD
pages - the seed migrations are the data source; updates arrive as later seed migrations).

#### Scenario: Tree query returns the nested divisions
- **WHEN** a client requests the city tree
- **THEN** the 中国 root returns with its province children nested from `up_id`

#### Scenario: List a city's districts
- **WHEN** a client requests the list filtered by the 北京市 city node (level 2)
- **THEN** only its level-3 districts return
- **AND** no write endpoint exists for the table
