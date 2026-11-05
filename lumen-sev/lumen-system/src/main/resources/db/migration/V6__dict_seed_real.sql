-- V6__dict_seed_real.sql - real platform dictionaries, curated from the OPS
-- reference database (rcp-test.dict_catal / dict_detail).
--
-- The user ruling (2026-11-05): the shared test database keeps only real data -
-- seed admin, the real menu tree, and real dictionaries copied from rcp-test.
-- This migration carries the curated copy so fresh deployments converge on the
-- same state; on the live shared test DB it was hand-applied (psql) right after
-- the physical residue purge, so every statement is ON CONFLICT-guarded and a
-- later Flyway run is a no-op.
--
-- Curation (6 catalogs / 28 details of the 51 live rcp catalogs):
--   INCLUDED - the person/org basics a RIS console actually renders:
--     1001 血型 (rcp code bloodType kept), 1003 婚姻, 1004 性别, 1005 职称,
--     1006 证件类型, 1007 机构类型.
--   EXCLUDED - rcp business-specific catalogs (合同/车辆/工单/筛查/AI/云胶片/
--     钉钉/...) and workflow-status catalogs (登记状态/报告状态/检查类型/患者类型/
--     登记类型/诊室类型/报告审核级别): those vocabularies belong to the
--     lumen-exam-core spec decisions, not to a wholesale OPS import.
--     1002 (name 年龄, code ORG_TYPE) is a mislabeled rcp row - excluded.
--
-- Mapping rules (rcp -> lumen):
--   enable   11 (the OPS enabled marker) -> 1.
--   label    rcp stores display text in `value` (label mostly NULL); lumen needs
--            label NOT NULL -> label = rcp.label when it is real display text,
--            else rcp.value. rcp's NUMERIC labels (机构类型 '10'/'11'/'12' group
--            codes) are spare data -> preserved in back_1, label falls to value.
--   code     catalogs without an rcp code get camelCase codes (marital/gender/
--            jobTitle/idType/orgType); detail numeric codes copy verbatim.
--   remark   rcp remarks are test junk ('1111', 'AEAEAE') -> dropped.
--   ids      catalog ids keep their rcp values (stable ca_id anchors). Detail ids
--            are OFFSET by 100000: rcp detail ids 1..350 collide with the lumen
--            common_status seed rows (ids 1-2). Nothing references detail ids
--            externally (details anchor by ca_id + ca_code), the offset only
--            keeps PKs deterministic; setval tails advance both sequences.

INSERT INTO public.dict_catal (id, code, name, enable, sort) VALUES
    (1001, 'bloodType', '血型',     1, 0),
    (1003, 'marital',   '婚姻',     1, 0),
    (1004, 'gender',    '性别',     1, 0),
    (1005, 'jobTitle',  '职称',     1, 0),
    (1006, 'idType',    '证件类型', 1, 0),
    (1007, 'orgType',   '机构类型', 1, 0)
ON CONFLICT DO NOTHING;

INSERT INTO public.dict_detail (id, ca_id, ca_code, code, value, label, color, css, sort, enable, back_1, initial) VALUES
    -- 血型 bloodType (rcp A row carries the green-badge display data verbatim)
    (100001, 1001, 'bloodType', 100101, 'A',  '绿色', '#69DC2F', 'display:block;', 1,  1, NULL, 'a'),
    (100002, 1001, 'bloodType', 100102, 'B',  'B',    NULL,      NULL,             0,  1, NULL, NULL),
    (100003, 1001, 'bloodType', 100103, 'AB', 'AB',   NULL,      NULL,             0,  1, NULL, NULL),
    (100004, 1001, 'bloodType', 100104, 'O',  'O',    NULL,      NULL,             0,  1, NULL, NULL),
    -- 婚姻 marital
    (100009, 1003, 'marital', 100301, '未婚', '未婚', NULL, NULL, 0, 1, NULL, NULL),
    (100010, 1003, 'marital', 100302, '已婚', '已婚', NULL, NULL, 0, 1, NULL, NULL),
    (100011, 1003, 'marital', 100303, '未知', '未知', NULL, NULL, 0, 1, NULL, NULL),
    -- 性别 gender
    (100012, 1004, 'gender', 100401, '男',   '男',   NULL, NULL, 0, 1, NULL, NULL),
    (100013, 1004, 'gender', 100402, '女',   '女',   NULL, NULL, 0, 1, NULL, NULL),
    (100014, 1004, 'gender', 100403, '未知', '未知', NULL, NULL, 0, 1, NULL, NULL),
    -- 职称 jobTitle (rcp sort 1-5, junior first)
    (100019, 1005, 'jobTitle', 100505, '技师',       '技师',       NULL, NULL, 1, 1, NULL, 'ji'),
    (100018, 1005, 'jobTitle', 100504, '医师',       '医师',       NULL, NULL, 2, 1, NULL, 'yi'),
    (100017, 1005, 'jobTitle', 100503, '主治医师',   '主治医师',   NULL, NULL, 3, 1, NULL, 'zhu'),
    (100016, 1005, 'jobTitle', 100502, '副主任医师', '副主任医师', NULL, NULL, 4, 1, NULL, 'fu'),
    (100015, 1005, 'jobTitle', 100501, '主任医师',   '主任医师',   NULL, NULL, 5, 1, NULL, 'zhu'),
    -- 证件类型 idType
    (100025, 1006, 'idType', 100601, '居民身份证', '居民身份证', NULL, NULL, 0, 1, NULL, NULL),
    (100026, 1006, 'idType', 100602, '护照',       '护照',       NULL, NULL, 0, 1, NULL, NULL),
    -- 机构类型 orgType (rcp numeric labels are group codes -> back_1)
    (100108, 1007, 'orgType', 100710, '平台机构',   '平台机构',   NULL, NULL, 1,  1, '10', 'ping'),
    (100031, 1007, 'orgType', 100705, '影像中心',   '影像中心',   NULL, NULL, 2,  1, '11', 'ying'),
    (100032, 1007, 'orgType', 100706, '基层卫生院', '基层卫生院', NULL, NULL, 3,  1, '12', 'ji'),
    (100033, 1007, 'orgType', 100707, '社区诊所',   '社区诊所',   NULL, NULL, 4,  1, '12', 'she'),
    (100034, 1007, 'orgType', 100708, '民营诊所',   '民营诊所',   NULL, NULL, 5,  1, '12', 'min'),
    (100027, 1007, 'orgType', 100701, '综合医院',   '综合医院',   NULL, NULL, 6,  1, '12', 'zong'),
    (100030, 1007, 'orgType', 100704, '体检中心',   '体检中心',   NULL, NULL, 8,  1, '12', 'ti'),
    (100028, 1007, 'orgType', 100702, '疾控中心',   '疾控中心',   NULL, NULL, 9,  1, '12', 'ji'),
    (100029, 1007, 'orgType', 100703, '妇幼保健院', '妇幼保健院', NULL, NULL, 10, 1, '12', 'fu'),
    (100350, 1007, 'orgType', 100711, '代理商',     '代理商',     NULL, NULL, 11, 1, '10', 'dai'),
    (100304, 1007, 'orgType', 100799, '其他',       '其他',       NULL, NULL, 99, 1, NULL, 'qi')
ON CONFLICT DO NOTHING;

-- Advance the seeded sequences past the explicit ids (V2/V5 discipline).
SELECT setval(pg_get_serial_sequence('public.dict_catal',  'id'), (SELECT MAX(id) FROM public.dict_catal));
SELECT setval(pg_get_serial_sequence('public.dict_detail', 'id'), (SELECT MAX(id) FROM public.dict_detail));
