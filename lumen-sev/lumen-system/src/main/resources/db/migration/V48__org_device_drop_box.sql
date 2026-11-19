-- V48__org_device_drop_box.sql - drop the Box (采集盒) columns from system.org_device
-- (org-device-drop-box, 2026-11-19). The web device form stopped editing the Box
-- parameters and the D9 NULL-aware full-replace update was nulling all five columns
-- on every save - the receive-to-overwrite pipeline served no live sender. The fields
-- leave the API contract, the entity, and both device DTOs in the same change
-- (specs/api delta: Device Create Validation, acquisition block narrows to the
-- 图像预设 capture/prune family).
--
-- The columns were created by V27 (applied history - never rewritten). DROP COLUMN
-- discards the stored values permanently: accepted by the user ruling (the params
-- are unneeded and the values were already being nulled on every edit). IF EXISTS
-- keeps the statements re-runnable after a delete-history-row-and-rerun recovery.

ALTER TABLE system.org_device DROP COLUMN IF EXISTS box_adapter;
ALTER TABLE system.org_device DROP COLUMN IF EXISTS box_ip;
ALTER TABLE system.org_device DROP COLUMN IF EXISTS box_port;
ALTER TABLE system.org_device DROP COLUMN IF EXISTS box_mac;
ALTER TABLE system.org_device DROP COLUMN IF EXISTS box_print;
