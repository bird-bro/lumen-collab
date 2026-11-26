-- V55__registration_status_dict.sql - the 「登记单状态」 (1031) dict catalog
-- (registration-order-status-dict-sev task 2.2; user ruling 2026-11-26 with the id
-- assigned): the registration desk's status LABELS move from frontend constants into
-- the dict system - the six machine statuses (已登记/检查中/诊断中/已完成/已打印/已取消)
-- as code = 目录ID前缀 (catalId*100 + seq, the rcp 性别 100401/100402 anchor-family
-- shape - the user's 2026-11-26 code correction superseding the draft's bare 1-6),
-- value = the 中文 label, sort = the chip order. The bridge (enum-name ↔ code ↔
-- fallback label) is the recorded cross-stack contract in
-- openspec/coordination/registration-order-status-dict.md; the stored status token
-- STAYS the backend enum name (the dict is the label authority only).
--
-- Id bookkeeping: 1031 is free (verified 2026-11-26 - the live catalog set tops out
-- at 1029; 1030 unclaimed), so the rows are plain additive inserts; ON CONFLICT (id)
-- DO NOTHING keeps a re-run a no-op and the setval tails keep the application-side
-- sequences clear of the seeded ids (the V44 discipline). Detail ids follow the V44
-- precedent (catalId*1000 + seq).
--
-- History note: the first-applied shape of THIS uncommitted migration seeded bare
-- codes 1-6; the user re-anchored the codes to the catalog-id-prefixed family
-- through 字典管理 the same day (the never-rewrite ruling's sanctioned path - the
-- history row was dropped and this file re-applied, ON CONFLICT keeping the
-- operator-corrected rows).

INSERT INTO public.dict_catal (id, name, enable, sort, remark) VALUES
    (1031, '登记单状态', 1, 99, '登记单六态词表(us 工作台;2026-11-26 用户拍板,内核六态机的标签权威)')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO public.dict_detail (id, ca_id, code, value, tag, sort, enable) VALUES
    (1031001, 1031, 103101, '已登记', 'blue',  1, 1),
    (1031002, 1031, 103102, '检查中', 'amber', 2, 1),
    (1031003, 1031, 103103, '诊断中', 'amber', 3, 1),
    (1031004, 1031, 103104, '已完成', 'green', 4, 1),
    (1031005, 1031, 103105, '已打印', 'green', 5, 1),
    (1031006, 1031, 103106, '已取消', 'gray',  6, 1)
    ON CONFLICT (id) DO NOTHING;

-- The tag backfill: the replay path (the never-rewrite ruling's sanctioned history-row
-- drop) meets rows the operator already code-corrected through 字典管理 — ON CONFLICT
-- skips them, so the tags fill in here. Idempotent: the WHERE goes false after the
-- first pass; operator-tuned tags (non-empty) are never overwritten.
UPDATE public.dict_detail SET tag = v.tag
FROM (VALUES
    (103101, 'blue'),
    (103102, 'amber'),
    (103103, 'amber'),
    (103104, 'green'),
    (103105, 'green'),
    (103106, 'gray')
) AS v(code, tag)
WHERE dict_detail.ca_id = 1031 AND dict_detail.code = v.code
  AND (dict_detail.tag IS NULL OR dict_detail.tag = '');

SELECT setval(pg_get_serial_sequence('public.dict_catal', 'id'),
              (SELECT MAX(id) FROM public.dict_catal));
SELECT setval(pg_get_serial_sequence('public.dict_detail', 'id'),
              (SELECT MAX(id) FROM public.dict_detail));
