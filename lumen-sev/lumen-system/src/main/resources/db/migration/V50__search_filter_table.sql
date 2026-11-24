-- V50__search_filter_table.sql - the generic saved-search persistence (拍板 07-A,
-- us-registration-redesign-api task 5.1, design D3): one workbench-agnostic table
-- every console list may persist filters against - uid + page_key + name + query
-- jsonb (the per-page filter shape passes through VERBATIM; its contract belongs to
-- each workbench) + the default flag. ONE default per (uid, page_key) among LIVE
-- rows - the partial unique index is the BACKSTOP; the endpoint's full-replace is
-- the mechanism (the role_menu assign precedent). Column settings deliberately stay
-- localStorage (the EIS useHeaderColumn pattern - frontend-only, proposal ③).
--
-- Conventions: serial Integer PK, EntityBase audit tail, zero physical FKs (uid is
-- the logical user_info reference), soft delete via @TableLogic. IDEMPOTENCE:
-- re-runs as CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS. COMMENT LINES
-- CARRY NO SEMICOLONS (the re-execution test splits statements on semicolons).

CREATE TABLE IF NOT EXISTS search_filter (
    id         serial PRIMARY KEY,
    uid        integer      NOT NULL,       -- 归属用户 (user_info.uid 逻辑引用，无物理 FK)
    page_key   varchar(64)  NOT NULL,       -- 工作台页区分键 (每页一个命名空间)
    name       varchar(64)  NOT NULL,       -- 筛选项名称
    query      jsonb,                       -- 过滤条件原文 (jsonb 透传，形状归各工作台)
    is_default integer      NOT NULL DEFAULT 0, -- 页面默认筛选 (0/1；每 uid+page_key 至多一个 live)
    deleted    integer      NOT NULL DEFAULT 0,
    create_by  integer,
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by  integer,
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS search_filter_uid_page_idx
    ON search_filter (uid, page_key) WHERE deleted = 0;

-- the one-default backstop: among LIVE rows, (uid, page_key) admits ONE default
CREATE UNIQUE INDEX IF NOT EXISTS search_filter_default_uidx
    ON search_filter (uid, page_key) WHERE is_default = 1 AND deleted = 0;
