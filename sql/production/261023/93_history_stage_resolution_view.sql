-- 93_history_stage_resolution_view.sql
-- Resolves precise request stage codes in SES_HISTORY_V for accurate filtering and display

CREATE OR REPLACE VIEW ses_history_v AS
SELECT 'SES_REQUEST:' || r.request_id         AS history_key,
       'SES_REQUEST'                          AS business_type,
       r.request_id                           AS business_ref_id,
       NVL(t.type_name, r.type_code)          AS module_name,
       r.request_no                           AS transaction_no,
       r.requestor_user_id                    AS requestor_user_id,
       CASE
           WHEN r.status = 'DRAFT' THEN 'DRAFT'
           WHEN r.status = 'WAITING_FOR_UPLOAD' THEN 'WAITING_FOR_UPLOAD'
           WHEN r.status = 'APPROVED' THEN 'APPROVED'
           WHEN r.status = 'REJECTED' THEN 'REJECTED'
           WHEN r.status = 'CANCELLED' THEN 'CANCELLED'
           WHEN r.status = 'PENDING' THEN
               CASE
                   WHEN EXISTS (
                       SELECT 1 FROM ses_approval_task_chkp c1 
                       WHERE c1.task_id = ai.current_task_id AND c1.checkpoint_key = 'PAYMENT_VERIFICATION' AND c1.status = 'PENDING'
                   ) THEN 'WAITING_PAYMENT'
                   WHEN EXISTS (
                       SELECT 1 FROM ses_approval_task_chkp c2 
                       WHERE c2.task_id = ai.current_task_id AND c2.checkpoint_key = 'SCREENING_SCHEDULE' AND c2.status = 'PENDING'
                   ) THEN 'WAITING_SCREENING'
                   WHEN EXISTS (
                       SELECT 1 FROM ses_approval_task_chkp c3 
                       WHERE c3.task_id = ai.current_task_id AND c3.checkpoint_key = 'BADGE_RETRIEVAL_SCHEDULE' AND c3.status = 'PENDING'
                   ) THEN 'WAITING_RETRIEVAL'
                   ELSE 'WAITING_APPROVAL'
               END
           ELSE r.status
       END                                    AS status,
       r.created_date                         AS created_date,
       r.approval_instance_id                 AS approval_instance_id
  FROM ses_request r
  LEFT JOIN ses_request_type t ON t.type_code = r.type_code
  LEFT JOIN ses_approval_instance ai ON r.approval_instance_id = ai.instance_id
UNION ALL
SELECT 'SES_STICKER_REQUEST:' || s.sticker_request_id,
       'SES_STICKER_REQUEST',
       s.sticker_request_id,
       'Stiker Kendaraan',
       s.request_no,
       s.requestor_user_id,
       CASE
           WHEN s.status = 'DRAFT' THEN 'DRAFT'
           WHEN s.status = 'APPROVED' THEN 'APPROVED'
           WHEN s.status = 'REJECTED' THEN 'REJECTED'
           WHEN s.status = 'CANCELLED' THEN 'CANCELLED'
           WHEN s.status = 'PENDING' THEN
               CASE
                   WHEN EXISTS (
                       SELECT 1 FROM ses_approval_task_chkp c1 
                       WHERE c1.task_id = ai_s.current_task_id AND c1.checkpoint_key = 'PAYMENT_VERIFICATION' AND c1.status = 'PENDING'
                   ) THEN 'WAITING_PAYMENT'
                   WHEN EXISTS (
                       SELECT 1 FROM ses_approval_task_chkp c2 
                       WHERE c2.task_id = ai_s.current_task_id AND c2.checkpoint_key = 'SCREENING_SCHEDULE' AND c2.status = 'PENDING'
                   ) THEN 'WAITING_SCREENING'
                   WHEN EXISTS (
                       SELECT 1 FROM ses_approval_task_chkp c3 
                       WHERE c3.task_id = ai_s.current_task_id AND c3.checkpoint_key = 'BADGE_RETRIEVAL_SCHEDULE' AND c3.status = 'PENDING'
                   ) THEN 'WAITING_RETRIEVAL'
                   ELSE 'WAITING_APPROVAL'
               END
           ELSE s.status
       END                                    AS status,
       s.created_date,
       s.approval_instance_id
  FROM ses_sticker_request s
  LEFT JOIN ses_approval_instance ai_s ON s.approval_instance_id = ai_s.instance_id;
