-- V45__number_stamp_format_rename.sql - the stamp-format catalog follows the
-- operator's rename (user action 2026-11-19: 号码戳格式 → 编号格式, dict UI).
-- The by-name anchor (V12 discipline) is the cross-stack contract, so the seed
-- name and the frontend constant move together: on THIS database the rename
-- already happened (the UPDATE matches nothing, idempotent); on FRESH installs
-- V43 seeds 号码戳格式 and THIS migration lands the final 编号格式 name.
UPDATE public.dict_catal SET name = '编号格式'
 WHERE id = 1029 AND name = '号码戳格式' AND deleted = 0;
