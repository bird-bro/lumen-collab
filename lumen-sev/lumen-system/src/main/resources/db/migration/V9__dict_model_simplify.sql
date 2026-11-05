-- V9__dict_model_simplify.sql - dict detail model slim-down (user ruling 2026-11-05).
--
-- Field rulings on the copied OPS dict shape:
--   ca_code  DROP  - the denormalized catalog-code anchor is unnecessary; lookups
--                    resolve the catalog by its unique live code (dict_catal) and
--                    filter by ca_id. The detail-level unique constraint rebuilds on
--                    (ca_id, value) - same partial shape, live rows only.
--   label    DROP  - value and label duplicated (rcp stores display text in value;
--                    lumen seeds mirrored it into label). value becomes THE text
--                    field (display + stored text); `code` stays the machine key.
--                    Data carry BEFORE the drop: the two lumen-seeded common_status
--                    rows hold a machine value ('1'/'0') plus real display text
--                    (启用/停用) - their label moves into value so the status dict
--                    keeps rendering human text.
--   back_1   RENAME to tag (spare field 1 -> semantic name)
--   back_2   RENAME to flag (spare field 2 -> semantic name)
--   initial  DROP  - pinyin-initial search helper from the OPS lineage; no lumen
--                    consumer (verified: no code reads it). Droppable without loss;
--                    re-add via migration if a letter-index filter is ever wanted.
--                    Dropped on BOTH dict tables (dict_catal carries it too).
--
-- rcp-copied rows are untouched by the carry: their value already IS the display
-- text (the 血型-A row keeps value 'A' + its green color; the leftover '绿色' label
-- dies with the column).
--
-- NOT hand-applied to the shared test DB: the column drops break the RUNNING
-- service's dict endpoints until it restarts with the matching entity code - the
-- migration and the code land together at the next lumen-system boot. All
-- statements are naturally idempotent-shape (ALTERs fail loudly on re-run only
-- when Flyway re-runs out of history, which it does not).

UPDATE public.dict_detail SET value = label
 WHERE ca_code = 'common_status' AND label IS NOT NULL AND value <> label;

DROP INDEX IF EXISTS public.uk_dict_detail_ca_code_value;
CREATE UNIQUE INDEX uk_dict_detail_ca_id_value ON public.dict_detail (ca_id, value) WHERE deleted = 0;

ALTER TABLE public.dict_detail DROP COLUMN ca_code;
ALTER TABLE public.dict_detail DROP COLUMN label;
ALTER TABLE public.dict_detail DROP COLUMN initial;
ALTER TABLE public.dict_detail RENAME COLUMN back_1 TO tag;
ALTER TABLE public.dict_detail RENAME COLUMN back_2 TO flag;

ALTER TABLE public.dict_catal DROP COLUMN initial;
