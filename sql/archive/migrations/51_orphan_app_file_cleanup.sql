-- ============================================================
-- SES — Reconciliation: app_file rows orphaned by the old worker save path
-- Oracle 11g / 12c+.
--
-- ⚠️ NOT PART OF THE RUN ORDER. Never execute this as part of a deploy. It is
--    a one-off clean-up, to be run deliberately by an operator who has read
--    the audit output in Part 1 first.
--
-- WHAT LEAKED, AND WHY
-- Before the 2026-10-06 wave, RequestService#saveAndSubmit saved worker data by
-- DELETING every ses_request_worker row of the request and re-inserting the
-- lot. fk_ses_wdoc_worker is ON DELETE CASCADE, so the ses_request_worker_doc
-- rows went with them — but the BLOBs those rows pointed at did not. No code
-- path in this application has ever called AppFileService.delete (the method
-- exists; it had no callers), so every re-save of a request left one orphaned
-- app_file row per attached document behind, with its full binary payload.
--
-- Stage 4 of that wave replaces the delete-and-reinsert path with a per-worker
-- upsert that deletes a replaced file explicitly, so the leak has no source
-- any more. This script only cleans up what the old path already left.
--
-- WHY IT MATCHES ON file_id AND NOT ON THE worker_id INSIDE ref_id
-- ref_id follows the '<OWNER_TABLE>:<pk>' convention, so the obvious filter
-- would be "every SES_REQUEST_WORKER:* row whose worker_id no longer exists".
-- That filter is WRONG and would delete live files. AppFileService.reassign
-- carries a file forward across an edit by rewriting ref_id, and the old
-- carry-forward could leave a still-referenced file pointing at a worker row
-- that has since been deleted. Matching on "no ses_request_worker_doc row
-- anywhere references this file_id" is the only safe test: it asks whether
-- anything still points AT the file, rather than guessing from what the file
-- points at.
--
-- Payment proofs, sticker letters and goods attachments are untouched — the
-- filter is scoped to ref_id LIKE 'SES_REQUEST_WORKER:%'.
-- ============================================================

-- ============================================================
-- PART 1 — AUDIT (read-only). Run this first and read the numbers.
-- ============================================================

PROMPT === Orphaned worker-document files: count and total bytes ===
SELECT COUNT(*)                         AS orphan_rows,
       NVL(SUM(f.file_size), 0)         AS orphan_bytes,
       ROUND(NVL(SUM(f.file_size), 0) / 1024 / 1024, 2) AS orphan_mb
FROM   app_file f
WHERE  f.ref_id LIKE 'SES_REQUEST_WORKER:%'
AND    NOT EXISTS (
           SELECT 1 FROM ses_request_worker_doc d WHERE d.file_id = f.file_id
       );

PROMPT === For comparison: worker-document files still referenced ===
SELECT COUNT(*)                         AS live_rows,
       ROUND(NVL(SUM(f.file_size), 0) / 1024 / 1024, 2) AS live_mb
FROM   app_file f
WHERE  f.ref_id LIKE 'SES_REQUEST_WORKER:%'
AND    EXISTS (
           SELECT 1 FROM ses_request_worker_doc d WHERE d.file_id = f.file_id
       );

PROMPT === Oldest and newest orphan, as a sanity check on the date range ===
SELECT MIN(f.created_date) AS oldest_orphan,
       MAX(f.created_date) AS newest_orphan
FROM   app_file f
WHERE  f.ref_id LIKE 'SES_REQUEST_WORKER:%'
AND    NOT EXISTS (
           SELECT 1 FROM ses_request_worker_doc d WHERE d.file_id = f.file_id
       );

-- ============================================================
-- PART 2 — DELETE. Deliberately left commented out.
-- Uncomment and run ONLY after reading Part 1 and taking a backup.
-- ============================================================

--DELETE FROM app_file f
--WHERE  f.ref_id LIKE 'SES_REQUEST_WORKER:%'
--AND    NOT EXISTS (
--           SELECT 1 FROM ses_request_worker_doc d WHERE d.file_id = f.file_id
--       );
--
--COMMIT;
