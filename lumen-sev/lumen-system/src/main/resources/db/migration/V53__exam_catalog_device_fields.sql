-- V53__exam_catalog_device_fields.sql - the device-facing catalog fields + the demo
-- catalog seed (exam-catalog-device-fields; user request 2026-11-25: the MWL
-- worklist needs Latin device fields - some devices do not render Chinese).
--
-- PART 1 - the three additive device columns (all nullable - the Chinese originals
-- stay the only required identities; the future MWL composer reads them by item id):
--   exam_item.device_desc   varchar(128) - the Latin procedure description (SPS
--                           Description / Procedure Code Meaning; the item's code
--                           serves as the Procedure Code Value)
--   exam_body.device_token  varchar(32)  - the DICOM Body Part Examined defined term
--                           (CHEST/ABDOMEN/LSPINE ...)
--   exam_body.device_name   varchar(64)  - the Latin body name
--   exam_method.device_desc varchar(128) - the Latin method text (the link table
--                           exam_item_method carries no text)
--
-- PART 2 - the demo catalog seed, the FULL transitive closure in five tables (the
-- apply-time discovery - bodies hang off groups, items off bodies, links off
-- items+methods): 23 exam_body_group + 34 exam_body + 15 exam_method + 46
-- exam_item + 58 exam_item_method rows, embedded verbatim from the shared test DB
-- (remark = 演示种子 2026-11-24 on every row). The V52 idiom: explicit ids +
-- ON CONFLICT (id) DO NOTHING so the shared DB replays as a no-op and a fresh
-- schema inserts everything, then the setval tails keep the application-side
-- sequences above the seeded maxima (the sequence-drift hazard). The device
-- columns seed NULL here - the reviewed Latin values land via the backfill
-- UPDATEs added after the user review gate.
--
-- House DDL rules honored: COMMENT LINES CARRY NO SEMICOLONS (the V36 lesson).

ALTER TABLE system.exam_item ADD COLUMN IF NOT EXISTS device_desc varchar(128);
ALTER TABLE system.exam_body ADD COLUMN IF NOT EXISTS device_token varchar(32);
ALTER TABLE system.exam_body ADD COLUMN IF NOT EXISTS device_name varchar(64);
ALTER TABLE system.exam_method ADD COLUMN IF NOT EXISTS device_desc varchar(128);

INSERT INTO system.exam_body_group (id, modality, name, sort, enable, remark) VALUES
    (1189, 'US', '腹部', 1, 1, '演示种子 2026-11-24'),
    (1190, 'US', '泌尿系', 2, 1, '演示种子 2026-11-24'),
    (1191, 'US', '妇科', 3, 1, '演示种子 2026-11-24'),
    (1192, 'US', '产科', 4, 1, '演示种子 2026-11-24'),
    (1193, 'US', '心脏', 5, 1, '演示种子 2026-11-24'),
    (1194, 'US', '浅表器官', 6, 1, '演示种子 2026-11-24'),
    (1195, 'US', '血管', 7, 1, '演示种子 2026-11-24'),
    (1196, 'US', '其他', 8, 1, '演示种子 2026-11-24'),
    (1197, 'CT', '头颈', 1, 1, '演示种子 2026-11-24'),
    (1198, 'CT', '胸部', 2, 1, '演示种子 2026-11-24'),
    (1199, 'CT', '腹盆', 3, 1, '演示种子 2026-11-24'),
    (1200, 'CT', '脊柱四肢', 4, 1, '演示种子 2026-11-24'),
    (1201, 'CT', '血管', 5, 1, '演示种子 2026-11-24'),
    (1202, 'DR', '胸部', 10, 1, '演示种子 2026-11-24'),
    (1203, 'DR', '腹部', 20, 1, '演示种子 2026-11-24'),
    (1204, 'DR', '脊柱四肢', 30, 1, '演示种子 2026-11-24'),
    (1205, 'MR', '头颈', 1, 1, '演示种子 2026-11-24'),
    (1206, 'MR', '脊柱', 2, 1, '演示种子 2026-11-24'),
    (1207, 'MR', '腹盆', 3, 1, '演示种子 2026-11-24'),
    (1208, 'MR', '关节', 4, 1, '演示种子 2026-11-24'),
    (1209, 'ENDO', '胃镜', 1, 1, '演示种子 2026-11-24'),
    (1210, 'ENDO', '肠镜', 2, 1, '演示种子 2026-11-24'),
    (1211, 'ENDO', '支气管镜', 3, 1, '演示种子 2026-11-24')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO system.exam_body (id, modality, group_id, up_id, name, sort, enable, remark) VALUES
    (1011, 'US', 1189, NULL, '肝胆胰脾', 1, 1, '演示种子 2026-11-24'),
    (1012, 'US', 1190, NULL, '泌尿系', 1, 1, '演示种子 2026-11-24'),
    (1013, 'US', 1191, NULL, '子宫附件', 1, 1, '演示种子 2026-11-24'),
    (1014, 'US', 1192, NULL, '胎儿及附属物', 1, 1, '演示种子 2026-11-24'),
    (1015, 'US', 1193, NULL, '心脏', 1, 1, '演示种子 2026-11-24'),
    (1016, 'US', 1194, NULL, '甲状腺及颈部淋巴结', 1, 1, '演示种子 2026-11-24'),
    (1017, 'US', 1194, NULL, '乳腺及腋窝', 2, 1, '演示种子 2026-11-24'),
    (1018, 'US', 1194, NULL, '浅表其他', 3, 1, '演示种子 2026-11-24'),
    (1019, 'US', 1195, NULL, '颈部血管', 1, 1, '演示种子 2026-11-24'),
    (1020, 'US', 1195, NULL, '下肢血管', 2, 1, '演示种子 2026-11-24'),
    (1021, 'US', 1196, NULL, '胸腔', 1, 1, '演示种子 2026-11-24'),
    (1022, 'US', 1196, NULL, '胃肠', 2, 1, '演示种子 2026-11-24'),
    (1023, 'CT', 1197, NULL, '头颅', 1, 1, '演示种子 2026-11-24'),
    (1024, 'CT', 1197, NULL, '颈部', 2, 1, '演示种子 2026-11-24'),
    (1025, 'CT', 1198, NULL, '胸部', 1, 1, '演示种子 2026-11-24'),
    (1026, 'CT', 1199, NULL, '腹盆腔', 1, 1, '演示种子 2026-11-24'),
    (1027, 'CT', 1200, NULL, '腰椎', 1, 1, '演示种子 2026-11-24'),
    (1028, 'CT', 1200, NULL, '颈椎', 2, 1, '演示种子 2026-11-24'),
    (1029, 'CT', 1201, NULL, '头颈血管', 1, 1, '演示种子 2026-11-24'),
    (1030, 'CT', 1201, NULL, '冠状动脉', 2, 1, '演示种子 2026-11-24'),
    (1031, 'DR', 1202, NULL, '胸部', 1, 1, '演示种子 2026-11-24'),
    (1032, 'DR', 1203, NULL, '腹部', 1, 1, '演示种子 2026-11-24'),
    (1033, 'DR', 1204, NULL, '腰椎', 1, 1, '演示种子 2026-11-24'),
    (1034, 'DR', 1204, NULL, '膝关节', 2, 1, '演示种子 2026-11-24'),
    (1035, 'MR', 1205, NULL, '头颅', 1, 1, '演示种子 2026-11-24'),
    (1036, 'MR', 1206, NULL, '颈椎', 1, 1, '演示种子 2026-11-24'),
    (1037, 'MR', 1206, NULL, '胸椎', 2, 1, '演示种子 2026-11-24'),
    (1038, 'MR', 1206, NULL, '腰椎', 3, 1, '演示种子 2026-11-24'),
    (1039, 'MR', 1207, NULL, '上腹', 1, 1, '演示种子 2026-11-24'),
    (1040, 'MR', 1207, NULL, '盆腔', 2, 1, '演示种子 2026-11-24'),
    (1041, 'MR', 1208, NULL, '膝关节', 1, 1, '演示种子 2026-11-24'),
    (1042, 'ENDO', 1209, NULL, '食管胃十二指肠', 1, 1, '演示种子 2026-11-24'),
    (1043, 'ENDO', 1210, NULL, '结直肠', 1, 1, '演示种子 2026-11-24'),
    (1044, 'ENDO', 1211, NULL, '气管支气管', 1, 1, '演示种子 2026-11-24')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO system.exam_method (id, modality, name, code, sort, enable, remark) VALUES
    (809, 'US', '经腹', 'US-M1', 1, 1, '演示种子 2026-11-24'),
    (810, 'US', '经阴道', 'US-M2', 2, 1, '演示种子 2026-11-24'),
    (811, 'US', '经直肠', 'US-M3', 3, 1, '演示种子 2026-11-24'),
    (812, 'US', '彩色多普勒', 'US-M4', 4, 1, '演示种子 2026-11-24'),
    (813, 'US', '超声造影', 'US-M5', 5, 1, '演示种子 2026-11-24'),
    (814, 'US', '弹性成像', 'US-M6', 6, 1, '演示种子 2026-11-24'),
    (815, 'CT', '平扫', 'CT-M1', 1, 1, '演示种子 2026-11-24'),
    (816, 'CT', '增强', 'CT-M2', 2, 1, '演示种子 2026-11-24'),
    (817, 'CT', 'CTA', 'CT-M3', 3, 1, '演示种子 2026-11-24'),
    (818, 'MR', '平扫', 'MR-M1', 1, 1, '演示种子 2026-11-24'),
    (819, 'MR', 'MRCP', 'MR-M2', 2, 1, '演示种子 2026-11-24'),
    (823, 'MR', 'MRA', 'MR-M3', 3, 1, '演示种子 2026-11-24'),
    (820, 'DR', '常规摄影', 'DR-M1', 1, 1, '演示种子 2026-11-24'),
    (821, 'ENDO', '普通检查', 'EN-M1', 1, 1, '演示种子 2026-11-24'),
    (822, 'ENDO', '无痛检查', 'EN-M2', 2, 1, '演示种子 2026-11-24')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO system.exam_item (id, modality, exam, code, external_code, body_id, price, rate, tag, is_common, sort, enable, remark) VALUES
    (614, 'US', '腹部超声（肝胆胰脾）', 'US-ABD-01', NULL, 1011, 80, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (615, 'US', '泌尿系超声（双肾输尿管膀胱）', 'US-URI-01', NULL, 1012, 80, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (616, 'US', '泌尿系超声（含前列腺）', 'US-URI-02', NULL, 1012, 100, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (617, 'US', '妇科超声（经腹）', 'US-GYN-01', NULL, 1013, 80, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (618, 'US', '妇科超声（经阴道）', 'US-GYN-02', NULL, 1013, 100, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (619, 'US', '产前常规超声', 'US-OBS-01', NULL, 1014, 100, 1.00, 1, false, 1, 1, '演示种子 2026-11-24'),
    (620, 'US', 'NT超声（11-13+6周）', 'US-OBS-02', NULL, 1014, 150, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (621, 'US', '产前系统超声（20-24周）', 'US-OBS-03', NULL, 1014, 250, 1.00, 1, false, 3, 1, '演示种子 2026-11-24'),
    (622, 'US', '超声心动图（常规）', 'US-ECH-01', NULL, 1015, 240, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (623, 'US', '甲状腺超声（含颈部淋巴结）', 'US-SUP-01', NULL, 1016, 90, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (624, 'US', '乳腺超声（含腋窝）', 'US-SUP-02', NULL, 1017, 90, 1.00, 1, true, 2, 1, '演示种子 2026-11-24'),
    (625, 'US', '浅表小器官超声（其他）', 'US-SUP-03', NULL, 1018, 60, 1.00, 1, false, 3, 1, '演示种子 2026-11-24'),
    (626, 'US', '颈部血管超声', 'US-VAS-01', NULL, 1019, 120, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (627, 'US', '下肢动静脉超声', 'US-VAS-02', NULL, 1020, 150, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (628, 'US', '胸腔积液定位超声', 'US-OTH-01', NULL, 1021, 60, 1.00, 1, false, 1, 1, '演示种子 2026-11-24'),
    (629, 'US', '胃肠充盈造影超声', 'US-OTH-02', NULL, 1022, 120, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (630, 'CT', '头颅CT平扫', 'CT-HN-01', NULL, 1023, 300, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (631, 'CT', '头颅CT增强', 'CT-HN-02', NULL, 1023, 480, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (632, 'CT', '颈部CT平扫', 'CT-HN-03', NULL, 1024, 300, 1.00, 1, false, 3, 1, '演示种子 2026-11-24'),
    (633, 'CT', '胸部CT平扫', 'CT-TX-01', NULL, 1025, 350, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (634, 'CT', '胸部CT增强', 'CT-TX-02', NULL, 1025, 500, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (635, 'CT', '胸部低剂量CT筛查（体检）', 'CT-TX-03', NULL, 1025, 200, 1.00, 1, false, 3, 1, '演示种子 2026-11-24'),
    (636, 'CT', '全腹CT平扫', 'CT-AB-01', NULL, 1026, 400, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (637, 'CT', '全腹CT增强', 'CT-AB-02', NULL, 1026, 560, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (638, 'CT', '腰椎CT平扫', 'CT-SP-01', NULL, 1027, 350, 1.00, 1, false, 1, 1, '演示种子 2026-11-24'),
    (639, 'CT', '颈椎CT平扫', 'CT-SP-02', NULL, 1028, 350, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (640, 'CT', '头颈CTA（头颈血管成像）', 'CT-VA-01', NULL, 1029, 900, 1.00, 1, false, 1, 1, '演示种子 2026-11-24'),
    (641, 'CT', '冠脉CTA（冠状动脉成像）', 'CT-VA-02', NULL, 1030, 1000, 1.00, 1, true, 2, 1, '演示种子 2026-11-24'),
    (642, 'DR', '胸部正位摄影（体检）', 'DR-TX-02', NULL, 1031, 50, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (643, 'DR', '胸部正侧位摄影', 'DR-TX-01', NULL, 1031, 80, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (644, 'DR', '腹部立卧位平片', 'DR-AB-01', NULL, 1032, 80, 1.00, 1, false, 1, 1, '演示种子 2026-11-24'),
    (645, 'DR', '腰椎正侧位摄影', 'DR-SP-01', NULL, 1033, 90, 1.00, 1, false, 1, 1, '演示种子 2026-11-24'),
    (646, 'DR', '膝关节正侧位摄影', 'DR-SP-02', NULL, 1034, 80, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (647, 'MR', '头颅MRI平扫', 'MR-HN-01', NULL, 1035, 600, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (648, 'MR', '头颅MRA', 'MR-HN-02', NULL, 1035, 850, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (649, 'MR', '颈椎MRI平扫', 'MR-SP-01', NULL, 1036, 650, 1.00, 1, false, 1, 1, '演示种子 2026-11-24'),
    (650, 'MR', '胸椎MRI平扫', 'MR-SP-02', NULL, 1037, 650, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (651, 'MR', '腰椎MRI平扫', 'MR-SP-03', NULL, 1038, 650, 1.00, 1, true, 3, 1, '演示种子 2026-11-24'),
    (652, 'MR', '上腹MRI平扫+MRCP', 'MR-AB-01', NULL, 1039, 780, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (653, 'MR', '盆腔MRI平扫', 'MR-AB-02', NULL, 1040, 650, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (654, 'MR', '膝关节MRI平扫', 'MR-JO-01', NULL, 1041, 600, 1.00, 1, false, 1, 1, '演示种子 2026-11-24'),
    (655, 'ENDO', '无痛胃镜', 'EN-GS-02', NULL, 1042, 600, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (656, 'ENDO', '普通胃镜', 'EN-GS-01', NULL, 1042, 300, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (657, 'ENDO', '无痛肠镜', 'EN-SC-02', NULL, 1043, 650, 1.00, 1, false, 2, 1, '演示种子 2026-11-24'),
    (658, 'ENDO', '普通肠镜', 'EN-SC-01', NULL, 1043, 350, 1.00, 1, true, 1, 1, '演示种子 2026-11-24'),
    (659, 'ENDO', '支气管镜检查', 'EN-BR-01', NULL, 1044, 400, 1.00, 1, false, 1, 1, '演示种子 2026-11-24')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO system.exam_item_method (id, exam_id, method_id) VALUES
    (398, 617, 809),
    (399, 618, 810),
    (400, 629, 813),
    (401, 630, 815),
    (402, 632, 815),
    (403, 633, 815),
    (404, 635, 815),
    (405, 636, 815),
    (406, 638, 815),
    (407, 639, 815),
    (408, 631, 816),
    (409, 634, 816),
    (410, 637, 816),
    (411, 640, 817),
    (412, 641, 817),
    (413, 647, 818),
    (414, 649, 818),
    (415, 650, 818),
    (416, 651, 818),
    (417, 652, 818),
    (418, 653, 818),
    (419, 654, 818),
    (420, 652, 819),
    (421, 614, 809),
    (422, 615, 809),
    (423, 616, 809),
    (424, 619, 809),
    (425, 620, 809),
    (426, 621, 809),
    (427, 616, 811),
    (428, 614, 812),
    (429, 615, 812),
    (430, 616, 812),
    (431, 617, 812),
    (432, 618, 812),
    (433, 619, 812),
    (434, 621, 812),
    (435, 622, 812),
    (436, 623, 812),
    (437, 624, 812),
    (438, 625, 812),
    (439, 626, 812),
    (440, 627, 812),
    (441, 629, 812),
    (442, 623, 814),
    (443, 624, 814),
    (444, 643, 820),
    (445, 642, 820),
    (446, 644, 820),
    (447, 645, 820),
    (448, 646, 820),
    (449, 656, 821),
    (450, 658, 821),
    (451, 659, 821),
    (452, 655, 822),
    (453, 657, 822),
    (454, 628, 812),
    (455, 648, 823)
    ON CONFLICT (id) DO NOTHING;


-- PART 3 - the reviewed Latin device values (the D4 gate passed 2026-11-25): the
-- backfill converges the shared DB's existing rows with fresh installs. Idempotent
-- by construction - every statement sets the same values on the same keys.
-- Item rows key on the (modality, code) natural key scoped to the demo remark;
-- body and method rows key on their seeded ids.

UPDATE system.exam_item AS i SET device_desc = v.dd
FROM (VALUES

    ('US', 'US-ABD-01', 'US ABDOMEN COMPLETE'),
    ('US', 'US-URI-01', 'US URINARY TRACT'),
    ('US', 'US-URI-02', 'US URINARY TRACT WITH PROSTATE'),
    ('US', 'US-GYN-01', 'US PELVIS TRANSABDOMINAL'),
    ('US', 'US-GYN-02', 'US PELVIS TRANSVAGINAL'),
    ('US', 'US-OBS-01', 'US OBSTETRIC ROUTINE'),
    ('US', 'US-OBS-02', 'US OBSTETRIC NT (11-13+6W)'),
    ('US', 'US-OBS-03', 'US OBSTETRIC SYSTEMATIC (20-24W)'),
    ('US', 'US-ECH-01', 'US ECHOCARDIOGRAPHY'),
    ('US', 'US-SUP-01', 'US THYROID WITH CERVICAL LYMPH NODES'),
    ('US', 'US-SUP-02', 'US BREAST WITH AXILLA'),
    ('US', 'US-SUP-03', 'US OTHER SUPERFICIAL STRUCTURES'),
    ('US', 'US-VAS-01', 'US CAROTID DUPLEX'),
    ('US', 'US-VAS-02', 'US LOWER LIMB ARTERIES AND VEINS'),
    ('US', 'US-OTH-01', 'US PLEURAL EFFUSION LOCALIZATION'),
    ('US', 'US-OTH-02', 'US GI CONTRAST SONOGRAPHY'),
    ('CT', 'CT-HN-01', 'CT HEAD PLAIN'),
    ('CT', 'CT-HN-02', 'CT HEAD ENHANCED'),
    ('CT', 'CT-HN-03', 'CT NECK PLAIN'),
    ('CT', 'CT-TX-01', 'CT CHEST PLAIN'),
    ('CT', 'CT-TX-02', 'CT CHEST ENHANCED'),
    ('CT', 'CT-TX-03', 'CT CHEST LOW-DOSE SCREENING'),
    ('CT', 'CT-AB-01', 'CT ABDOMEN PLAIN'),
    ('CT', 'CT-AB-02', 'CT ABDOMEN ENHANCED'),
    ('CT', 'CT-SP-01', 'CT LSPINE PLAIN'),
    ('CT', 'CT-SP-02', 'CT CSPINE PLAIN'),
    ('CT', 'CT-VA-01', 'CT HEADNECK CTA'),
    ('CT', 'CT-VA-02', 'CT CORONARY CTA'),
    ('DR', 'DR-TX-02', 'DR CHEST PA SCREENING'),
    ('DR', 'DR-TX-01', 'DR CHEST PA AND LATERAL'),
    ('DR', 'DR-AB-01', 'DR ABDOMEN ERECT AND SUPINE'),
    ('DR', 'DR-SP-01', 'DR LSPINE PA AND LATERAL'),
    ('DR', 'DR-SP-02', 'DR KNEE AP AND LATERAL'),
    ('MR', 'MR-HN-01', 'MR HEAD PLAIN'),
    ('MR', 'MR-HN-02', 'MR HEAD MRA'),
    ('MR', 'MR-SP-01', 'MR CSPINE PLAIN'),
    ('MR', 'MR-SP-02', 'MR TSPINE PLAIN'),
    ('MR', 'MR-SP-03', 'MR LSPINE PLAIN'),
    ('MR', 'MR-AB-01', 'MR UPPER ABDOMEN WITH MRCP'),
    ('MR', 'MR-AB-02', 'MR PELVIS PLAIN'),
    ('MR', 'MR-JO-01', 'MR KNEE PLAIN'),
    ('ENDO', 'EN-GS-02', 'PAINLESS GASTROSCOPY'),
    ('ENDO', 'EN-GS-01', 'GASTROSCOPY'),
    ('ENDO', 'EN-SC-02', 'PAINLESS COLONOSCOPY'),
    ('ENDO', 'EN-SC-01', 'COLONOSCOPY'),
    ('ENDO', 'EN-BR-01', 'BRONCHOSCOPY')

) AS v(m, code, dd)
WHERE i.modality = v.m AND i.code = v.code AND i.remark = '演示种子 2026-11-24';

UPDATE system.exam_body AS b SET device_token = v.token, device_name = v.name
FROM (VALUES

    (1011, 'ABDOMEN_UPPER', 'Liver, gallbladder, pancreas and spleen'),
    (1012, 'ABDOMEN', 'Urinary system'),
    (1013, 'UTERUS', 'Uterus with adnexa'),
    (1014, 'FETUS', 'Fetus and adnexa'),
    (1015, 'HEART', 'Heart'),
    (1016, 'THYROID', 'Thyroid with cervical lymph nodes'),
    (1017, 'BREAST', 'Breast with axilla'),
    (1018, 'SUPERFICIAL', 'Other superficial structures'),
    (1019, 'CAROTID', 'Cervical vessels'),
    (1020, 'LEG_VESSELS', 'Lower-limb vessels'),
    (1021, 'CHEST', 'Pleural space'),
    (1022, 'GI_TRACT', 'Stomach and bowel'),
    (1023, 'HEAD', 'Head'),
    (1024, 'NECK', 'Neck'),
    (1025, 'CHEST', 'Chest'),
    (1026, 'ABDOMEN', 'Abdomen and pelvis'),
    (1027, 'LSPINE', 'Lumbar spine'),
    (1028, 'CSPINE', 'Cervical spine'),
    (1029, 'CAROTID', 'Head-and-neck vessels'),
    (1030, 'CORONARY', 'Coronary arteries'),
    (1031, 'CHEST', 'Chest'),
    (1032, 'ABDOMEN', 'Abdomen'),
    (1033, 'LSPINE', 'Lumbar spine'),
    (1034, 'KNEE', 'Knee'),
    (1035, 'HEAD', 'Head'),
    (1036, 'CSPINE', 'Cervical spine'),
    (1037, 'TSPINE', 'Thoracic spine'),
    (1038, 'LSPINE', 'Lumbar spine'),
    (1039, 'ABDOMEN_UPPER', 'Upper abdomen'),
    (1040, 'PELVIS', 'Pelvis'),
    (1041, 'KNEE', 'Knee'),
    (1042, 'STOMACH', 'Esophagus, stomach and duodenum'),
    (1043, 'COLON', 'Colon and rectum'),
    (1044, 'BRONCHUS', 'Trachea and bronchi')

) AS v(id, token, name)
WHERE b.id = v.id;

UPDATE system.exam_method AS m SET device_desc = v.dd
FROM (VALUES

    (809, 'Transabdominal'),
    (810, 'Transvaginal'),
    (811, 'Transrectal'),
    (812, 'Color Doppler'),
    (813, 'Contrast-enhanced US'),
    (814, 'Elastography'),
    (815, 'Plain scan'),
    (816, 'Enhanced'),
    (817, 'CT angiography'),
    (818, 'Plain scan'),
    (819, 'MRCP'),
    (823, 'MR angiography'),
    (820, 'Routine radiography'),
    (821, 'Routine examination'),
    (822, 'Painless examination')

) AS v(id, dd)
WHERE m.id = v.id;

-- the setval tails (five sequences - the application-side inserts must never
-- collide with the seeded ids)
SELECT setval(pg_get_serial_sequence('system.exam_body_group', 'id'),
              (SELECT MAX(id) FROM system.exam_body_group));
SELECT setval(pg_get_serial_sequence('system.exam_body', 'id'),
              (SELECT MAX(id) FROM system.exam_body));
SELECT setval(pg_get_serial_sequence('system.exam_method', 'id'),
              (SELECT MAX(id) FROM system.exam_method));
SELECT setval(pg_get_serial_sequence('system.exam_item', 'id'),
              (SELECT MAX(id) FROM system.exam_item));
SELECT setval(pg_get_serial_sequence('system.exam_item_method', 'id'),
              (SELECT MAX(id) FROM system.exam_item_method));
