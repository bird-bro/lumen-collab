-- V23__role_type_dict_seed.sql - the 「角色类型」 dict catalog (role-type-dict-api,
-- coordination D1/D6): role_info.type's vocabulary moves to the data dictionary
-- (user ruling 2026-11-09 - "角色类型应该数据字典中维护,后面可能会有多种分类").
--
-- The two seed items mirror the copied RoleConstants lineage verbatim
-- (common/Constants/RoleConstants.java): TYPE_GLOBAL = 10 (系统角色), TYPE_RCP = 11
-- (RCP角色). Later categories are operator-managed rows in 字典管理 - zero code
-- change; the frontend renders the vocabulary through the by-name lookup
-- (role-type-dict-web).
--
-- Deterministic ids (V6 house style, ON CONFLICT-guarded so a replay is a no-op):
--   catalog id 1020  - clear of V6's 1001-1007 catalog band;
--   detail  ids 1020001/1020002 - clear of V6's +100000 offset band (100001+).
-- The catalog anchors BY NAME (V12 dropped the code column): 「角色类型」 is a
-- recorded cross-stack contract between the seed, the lookup endpoint and the
-- frontend consumer.

INSERT INTO public.dict_catal (id, name, enable, sort, remark) VALUES
    (1020, '角色类型', 1, 99, 'role_info.type 的取值词表(role-type-dict-api;新增分类直接在此维护)')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO public.dict_detail (id, ca_id, code, value, sort, enable) VALUES
    (1020001, 1020, 10, '系统角色', 1, 1),
    (1020002, 1020, 11, 'RCP角色',  2, 1)
    ON CONFLICT (id) DO NOTHING;
