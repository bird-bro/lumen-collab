-- V52__us_vocab_dict.sql - the 「患者来源」 (1023) and 「妊娠状态」 (1002) dict
-- catalogs (us-vocab-dict-catalogs; user ruling 2026-11-24 with the ids assigned):
-- the US registration workbench's patient_source / ext.pregnancy_status
-- vocabularies move from frontend constants into the dict system, alongside the
-- already-seeded 性别 (1004) / 证件类型 (1006) catalogs the page now also consumes.
--
-- Id bookkeeping: 1023 reuses the dissolved 诊室类型 band slot that V27 retired
-- UNSEEDED ("the id is retired, not reused") - it never landed in any migration,
-- so the slot is free and V27's retirement note is superseded by this ruling.
-- 1002 reuses the mislabeled rcp ORG_TYPE slot V6 excluded - likewise never
-- seeded. Neither id exists in prior seeds or the live catalog set (verified
-- 2026-11-24), so both rows are plain additive inserts; ON CONFLICT (id) DO
-- NOTHING keeps a re-run a no-op and the setval tails keep the application-side
-- sequences clear of the seeded ids (the V44 discipline).
--
-- The detail VALUE is the machine token stored verbatim in the string columns
-- (us order patient_source / ext.pregnancy_status keep the dict text - the
-- modality catalogs' value-keyed shape). Detail ids follow the V44 precedent
-- (catalId*1000 + seq). Vocabulary extension beyond the seed (e.g. more
-- patient sources) is operator work in 字典管理 - zero code change.

INSERT INTO public.dict_catal (id, name, enable, sort, remark) VALUES
    (1023, '患者来源', 1, 99, '登记单 patient_source 词表(us 工作台;2026-11-24 用户拍板,复用 V27 退役的诊室类型槽位)'),
    (1002, '妊娠状态', 1, 99, '登记单 ext.pregnancy_status 词表(us 工作台;2026-11-24 用户拍板,复用 V6 排除的 rcp ORG_TYPE 槽位)')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO public.dict_detail (id, ca_id, code, value, sort, enable) VALUES
    (1023001, 1023, 1, '门诊',   1, 1),
    (1023002, 1023, 2, '住院',   2, 1),
    (1023003, 1023, 3, '急诊',   3, 1),
    (1023004, 1023, 4, '体检',   4, 1),
    (1002001, 1002, 1, '未孕',   1, 1),
    (1002002, 1002, 2, '早孕',   2, 1),
    (1002003, 1002, 3, '中晚孕', 3, 1),
    (1002004, 1002, 4, '未知',   4, 1)
    ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('public.dict_catal', 'id'),
              (SELECT MAX(id) FROM public.dict_catal));
SELECT setval(pg_get_serial_sequence('public.dict_detail', 'id'),
              (SELECT MAX(id) FROM public.dict_detail));
