-- V7__dict_common_status_rebase.sql - re-base the common_status seed id to 1000.
--
-- User action 2026-11-05 (applied by hand on the shared test DB, both anchors
-- updated): the common_status catalog moves from V3's seed id 1 to 1000, sitting
-- at the head of the real-dictionary id range copied from rcp-test by V6
-- (1001 bloodType .. 1007 orgType) - one contiguous 1000-range for the whole
-- platform dictionary. The spec is id-agnostic (system-data "Dict Seed Data"
-- requires common_status to EXIST, it does not pin the id), so this re-base is
-- spec-clean; V3's id 1 remains the historical seed choice.
--
-- IDEMPOTENCE: guarded UPDATEs only - on the shared test DB (already at 1000)
-- both statements match zero rows; on a fresh database (V3 seeded id 1) they
-- converge it to the ruled state. Details anchor by ca_id AND ca_code; only the
-- ca_id half needs the move. Detail row ids (1-2) stay - nothing references
-- detail ids externally.

UPDATE public.dict_catal SET id = 1000, update_time = CURRENT_TIMESTAMP
 WHERE code = 'common_status' AND id = 1;

UPDATE public.dict_detail SET ca_id = 1000, update_time = CURRENT_TIMESTAMP
 WHERE ca_code = 'common_status' AND ca_id = 1;
