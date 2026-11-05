-- V12__dict_catal_code_drop.sql - the catalog code column dies (user ruling 2026-11-05).
--
-- dict_catal.code was the OPS-lineage stable lookup key (V3) and survived V9 as the
-- catalog anchor; the user ruling drops it outright - the form no longer asks for a
-- 目录编码, so the column has no writer left. Consequences carried here:
--
--   code      DROP  - together with its partial unique index uk_dict_catal_code.
--   name      UNIQUE among live rows - the display name is the only human-facing key
--             a catalog has left, so the A0714 duplicate rejection re-anchors on it
--             (uk_dict_catal_name, same partial shape as the dropped code index).
--   anchors   EVERYTHING anchors by ca_id now: the detail list endpoint switched
--             ?caCode= -> ?caId= (V9 already removed the denormalized detail-side
--             ca_code), and DictLabelHelper / DictReverseHelper take caId directly
--             (seed catalog ids are stable: 1000 common_status, 1001-1007 real dicts).
--
-- Test-residue cleanup (user ruling: DB 测试数据清理): the shared test database
-- carried 25 logically-deleted dict_catal rows (ids 1008-1032, the b2.*/ut.*
-- integration-test artifacts) plus 10 logically-deleted dict_detail rows hanging
-- under them. They are PHYSICALLY deleted here - live seed rows (1000-1007) stay.
--
-- IDEMPOTENCE: every statement is guarded (IF EXISTS / IF NOT EXISTS / guarded
-- DELETE), so the hand-applied run on the shared test DB and a later Flyway run
-- both converge silently - the same discipline V6/V7 used for hand-applied seeds.
-- The migration and the entity code land together at the next lumen-system boot.

DROP INDEX IF EXISTS public.uk_dict_catal_code;
ALTER TABLE public.dict_catal DROP COLUMN IF EXISTS code;
CREATE UNIQUE INDEX IF NOT EXISTS uk_dict_catal_name ON public.dict_catal (name) WHERE deleted = 0;

-- Physically remove the integration-test residue (already logically deleted).
DELETE FROM public.dict_catal WHERE deleted = 1;
DELETE FROM public.dict_detail WHERE deleted = 1;
