-- V47 (rep-temp-normal-range-sev 2026-11-19): one-shot rep_temp.content v1 -> v2
-- data migration (spec delta exam-setting "Normal-Range v2 One-Shot Migration").
-- Only rows whose content.version = 1 are rebuilt (idempotent guard - a replay
-- skips v2 rows, fresh installs with an empty column match zero rows). The strict
-- pattern set converts the v1 free-text normal into the v2 tagged union:
--   "19-40" / "21~37"     -> {"kind":"range","min":21,"max":40}
--   "<5" / "≤5"           -> {"kind":"range","min":null,"max":5}   (recorded flattening)
--   ">3" / "≥3"           -> {"kind":"range","min":3,"max":null}   (recorded flattening)
--   any other non-empty    -> {"kind":"text","text":ORIGINAL}  (verbatim bytes)
--   null                   -> null
-- The jsonb column and its V39 GIN index are untouched. Rebuild keeps section /
-- measure order (WITH ORDINALITY), the empty-measures array, and null measures.
UPDATE system.rep_temp AS t
SET content = rebuilt.new_content
FROM (
    SELECT r.id AS row_id,
           jsonb_build_object(
               'version', 2,
               'sections', sagg.sections,
               'impression', r.content->'impression'
           ) AS new_content
    FROM system.rep_temp AS r
    CROSS JOIN LATERAL (
        SELECT jsonb_agg(
                   jsonb_build_object(
                       'name', s.el->'name',
                       'finding', s.el->'finding',
                       'measures', COALESCE(mm.measures,
                                       CASE WHEN jsonb_typeof(s.el->'measures') = 'array'
                                            THEN '[]'::jsonb
                                            ELSE s.el->'measures' END)
                   ) ORDER BY s.ord) AS sections
        FROM jsonb_array_elements(
                 CASE WHEN jsonb_typeof(r.content->'sections') = 'array'
                      THEN r.content->'sections' ELSE NULL END
             ) WITH ORDINALITY AS s(el, ord)
        LEFT JOIN LATERAL (
            SELECT jsonb_agg(
                       jsonb_build_object(
                           'code', mv.el->'code',
                           'label', mv.el->'label',
                           'unit', mv.el->'unit',
                           'normal', CASE
                               WHEN mv.el->>'normal' IS NULL THEN NULL
                               WHEN mv.el->>'normal'
                                    ~ '^\s*(\d+(?:\.\d+)?)\s*[-~]\s*(\d+(?:\.\d+)?)\s*$'
                                   THEN jsonb_build_object(
                                       'kind', 'range',
                                       'min', (regexp_match(mv.el->>'normal',
                                               '^\s*(\d+(?:\.\d+)?)\s*[-~]\s*(\d+(?:\.\d+)?)\s*$'))[1]::numeric,
                                       'max', (regexp_match(mv.el->>'normal',
                                               '^\s*(\d+(?:\.\d+)?)\s*[-~]\s*(\d+(?:\.\d+)?)\s*$'))[2]::numeric)
                               WHEN mv.el->>'normal' ~ '^\s*[<≤]\s*(\d+(?:\.\d+)?)\s*$'
                                   THEN jsonb_build_object(
                                       'kind', 'range',
                                       'min', NULL::numeric,
                                       'max', (regexp_match(mv.el->>'normal',
                                               '^\s*[<≤]\s*(\d+(?:\.\d+)?)\s*$'))[1]::numeric)
                               WHEN mv.el->>'normal' ~ '^\s*[>≥]\s*(\d+(?:\.\d+)?)\s*$'
                                   THEN jsonb_build_object(
                                       'kind', 'range',
                                       'min', (regexp_match(mv.el->>'normal',
                                               '^\s*[>≥]\s*(\d+(?:\.\d+)?)\s*$'))[1]::numeric,
                                       'max', NULL::numeric)
                               ELSE jsonb_build_object('kind', 'text',
                                       'text', mv.el->>'normal')
                           END
                       ) ORDER BY mv.ord) AS measures
            FROM jsonb_array_elements(
                     CASE WHEN jsonb_typeof(s.el->'measures') = 'array'
                          THEN s.el->'measures' ELSE NULL END
                 ) WITH ORDINALITY AS mv(el, ord)
        ) mm ON jsonb_typeof(s.el->'measures') = 'array'
    ) sagg
    WHERE r.content->>'version' = '1'
      AND jsonb_typeof(r.content->'sections') = 'array'
) rebuilt
WHERE t.id = rebuilt.row_id;
