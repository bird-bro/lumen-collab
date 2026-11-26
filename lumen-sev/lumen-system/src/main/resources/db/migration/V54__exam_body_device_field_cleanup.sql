-- V54__exam_body_device_field_cleanup.sql - exam-body device-field cleanup
-- (exam-body-device-field-cleanup; user decision 2026-11-26): device_name had no
-- MWL consumer (Body Part Examined is the body's only device slot and it is
-- filled by the token itself) and device_token read as an auth/session token -
-- renamed to device_body_part to anchor the DICOM attribute. V53 stays untouched
-- (applied + archived, never rewritten); a fresh install replays its seed first
-- and these ALTERs then drop the dead column, so the shared DB converges here.
-- Two statements: PostgreSQL cannot combine RENAME into a comma-separated ALTER
-- action list (all actions except RENAME/SET SCHEMA may be combined).
--
-- House DDL rules honored: COMMENT LINES CARRY NO SEMICOLONS (the V36 lesson).

ALTER TABLE system.exam_body DROP COLUMN device_name;
ALTER TABLE system.exam_body RENAME COLUMN device_token TO device_body_part;
