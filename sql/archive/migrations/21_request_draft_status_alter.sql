-- ============================================================
-- SES — ses_request: add 'DRAFT' to the status CHECK constraint.
-- Run AFTER 03_request_ddl.sql (which creates + owns ses_request).
--
-- Supports the request form's "Save as Draft" action (RequestService.
-- saveDraft): a request can now be persisted with status='DRAFT' before
-- it has a valid approval-ready payload, then later submitted normally
-- (RequestService.saveAndSubmit, which now also accepts an existing
-- DRAFT row for its edit-permission check, same as PENDING). Plain
-- DROP+ADD CONSTRAINT, same convention as 11_request_worker_alter.sql —
-- does NOT drop or recreate the table. Not re-runnable once applied.
-- ============================================================

ALTER TABLE SES_REQUEST DROP CONSTRAINT CK_SES_REQUEST_STATUS;

ALTER TABLE SES_REQUEST ADD CONSTRAINT CK_SES_REQUEST_STATUS
    CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));

COMMIT;
