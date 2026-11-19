-- V43__number_stamp_format_dict.sql - the 「号码戳格式」 dict catalog (number-rule-
-- format-dict): number_rule.stamp's Java date/time patterns move from the drawer's
-- fixed yyMMdd behavior to an operator-maintained vocabulary.
--
-- The catalog anchors BY NAME (V12 discipline); the detail VALUE is the machine token
-- stored verbatim in number_rule.stamp and passed to the Java formatter. Later patterns
-- (including more timestamp granularities) are added in 字典管理 - zero code change.
-- Deterministic ids guarded ON CONFLICT (id) DO NOTHING: catalog 1029 (user ruling
-- 2026-11-19: use the 1029 catalog slot), details
-- 1029001-1029006 (the +100000 offset convention), with the setval tail
-- so the next application-side dict insert does not collide with seeded ids.

INSERT INTO public.dict_catal (id, name, enable, sort, remark) VALUES
    (1029, '号码戳格式', 1, 99, 'number_rule.stamp 的 Java 日期/时间模式词表(number-rule-format-dict;新增格式直接在此维护)')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO public.dict_detail (id, ca_id, code, value, sort, enable) VALUES
    (1029001, 1029, 1, 'yyMMdd',         1, 1),
    (1029002, 1029, 2, 'yyyyMMdd',       2, 1),
    (1029003, 1029, 3, 'yyMMddHHmm',     3, 1),
    (1029004, 1029, 4, 'yyyyMMddHHmm',   4, 1),
    (1029005, 1029, 5, 'yyMMddHHmmss',   5, 1),
    (1029006, 1029, 6, 'yyyyMMddHHmmss', 6, 1)
    ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('public.dict_catal', 'id'),
              (SELECT MAX(id) FROM public.dict_catal));
SELECT setval(pg_get_serial_sequence('public.dict_detail', 'id'),
              (SELECT MAX(id) FROM public.dict_detail));
