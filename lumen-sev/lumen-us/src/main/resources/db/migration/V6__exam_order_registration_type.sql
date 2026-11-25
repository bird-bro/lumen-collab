-- V6__exam_order_registration_type.sql - the kernel registration-category DDL,
-- vendor-copied into the us ledger (us-prereg-as-registered-sev task 2.2; source
-- lumen-core db/kernel/V3__exam_order_registration_type.sql, the V1/V4 vendoring
-- precedent; coordination parent openspec/coordination/us-prereg-as-registered.md,
-- user ruling 2026-11-24 预约=预登记).
--
-- exam_order gains the entry-category marker registration_type varchar(16) NOT NULL
-- DEFAULT 'WALK_IN' (values WALK_IN 现场登记 / PRE_REGISTERED 预约预登记, the ExamChannel
-- enum-name storage precedent). The category is a BUSINESS FACT stamped SERVER-SIDE by
-- the create branch - never accepted from a client body - and it is the discriminator
-- the queue system (排队叫号) consumes instead of a booking_time NULL/NOT NULL inference
-- (the booking_time slot anchor itself is UNCHANGED). The default backfills every
-- existing row as WALK_IN; legacy test-server pre-registration rows (BOOKED) are
-- re-stamped or hard-deleted in the shared-DB sweep, not by this migration.
--
-- House DDL rules honored here: serial integer ids, enum-name varchar storage, and
-- COMMENT LINES CARRY NO SEMICOLONS (the V36 house lesson).

ALTER TABLE exam_order ADD COLUMN IF NOT EXISTS registration_type varchar(16) NOT NULL DEFAULT 'WALK_IN';
