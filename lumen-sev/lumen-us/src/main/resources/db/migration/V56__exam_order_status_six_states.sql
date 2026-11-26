-- V56__exam_order_status_six_states.sql - the legacy status remap onto the six-state
-- machine (registration-order-status-dict-sev task 2.3; user ruling 2026-11-26): the
-- kernel ExamOrderStatus collapsed to the six registration statuses and the former
-- vocabulary must not survive as rows (a name-based enum handler explodes on read).
-- Mapping (design D5): BOOKING_CANCELLED -> CANCELLED (the single cancel landing),
-- BOOKED -> REGISTERED (预约=预登记 - the arrival-free model treats a booking as
-- registered), IN_PROGRESS -> IN_EXAM (处理中 meant exam-in-progress -> the 检查中
-- stage). Each WHERE goes false after the first pass - idempotent by construction.
-- The single-jar boot runs Flyway BEFORE serving, so the remap and the new enum land
-- together (design D5's accepted-rollback note lives in the change's design.md).

UPDATE exam_order SET status = 'CANCELLED'  WHERE status = 'BOOKING_CANCELLED';
UPDATE exam_order SET status = 'REGISTERED' WHERE status = 'BOOKED';
UPDATE exam_order SET status = 'IN_EXAM'    WHERE status = 'IN_PROGRESS';
