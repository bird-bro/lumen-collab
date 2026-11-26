-- V57__exam_order_exam_room.sql - the kernel exam-room anchor DDL, vendor-copied into
-- the us ledger (us-order-exam-room-sev task 1.3; source lumen-core
-- db/kernel/V4__exam_order_exam_room.sql, the V3 -> V6 vendoring precedent; coordination
-- parent openspec/coordination/us-order-exam-room.md, user ruling 2026-11-26 检查室必选).
--
-- exam_order gains the exam-room anchor room_id BIGINT NULL (us-order-exam-room ruling:
-- the room is chosen at creation - 预约时选房 for pre-registered orders, 现场选房 for
-- walk-ins - and the surface refuses a roomless body). NULL, not NOT NULL DEFAULT: no
-- natural default room, no legacy backfill - the requirement lives in the create-path
-- validation, legacy rows read NULL and still load. STACKS AFTER the active
-- registration-order-status-dict-sev pair (which owns V56).
--
-- House DDL rules honored here: BIGINT logical reference (no physical FK), COMMENT
-- LINES CARRY NO SEMICOLONS (the V36 house lesson).

ALTER TABLE exam_order ADD COLUMN IF NOT EXISTS room_id BIGINT NULL;
