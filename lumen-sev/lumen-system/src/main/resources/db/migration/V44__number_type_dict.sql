-- V44__number_type_dict.sql - the 「编号类型」 dict catalog (number-rule-type-dict,
-- user ruling 2026-11-19): number_rule.rule_type's family vocabulary moves from the
-- drawer's fixed ASCII codes (pat_id/accession/booking) to dict catalog 1028.
--
-- The catalog anchors BY NAME (V12 discipline) with the V43 seed shape; the detail
-- VALUE is the machine token stored verbatim in number_rule.rule_type and passed
-- through the issue API (patient 影像号; core 登记号). Later families (病理号/ ...)
-- are added in 字典管理 - zero code change. Deterministic ids guarded ON CONFLICT
-- (id) DO NOTHING: details 1028001-1028003 (the +100000 offset convention, codes 1-3
-- per the V43 precedent), with the setval tail so the next application-side dict
-- insert does not collide with seeded ids.

INSERT INTO public.dict_catal (id, name, enable, sort, remark) VALUES
    (1028, '编号类型', 1, 99, 'number_rule.rule_type 的号码族词表(number-rule-type-dict;新增号码族直接在此维护)')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO public.dict_detail (id, ca_id, code, value, sort, enable) VALUES
    (1028001, 1028, 1, '影像号', 1, 1),
    (1028002, 1028, 2, '登记号', 2, 1),
    (1028003, 1028, 3, '预约号', 3, 1)
    ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('public.dict_catal', 'id'),
              (SELECT MAX(id) FROM public.dict_catal));
SELECT setval(pg_get_serial_sequence('public.dict_detail', 'id'),
              (SELECT MAX(id) FROM public.dict_detail));

-- Re-anchor the V40-seeded family rows to the dict tokens (the uk_number_rule_active
-- unique index keys on rule_type, and the row-locked issuer resolves rules BY this
-- token - the issuance call sites re-anchor with the same commit: patient 影像号,
-- core 登记号). Content-anchored guards; re-runs match nothing.
UPDATE number_rule SET rule_type = '影像号' WHERE rule_type = 'pat_id' AND deleted = 0;
UPDATE number_rule SET rule_type = '登记号' WHERE rule_type = 'accession' AND deleted = 0;
UPDATE number_rule SET rule_type = '预约号' WHERE rule_type = 'booking' AND deleted = 0;
