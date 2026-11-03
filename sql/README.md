# SES — Database

The SES app runs against the **platform schema** (the shared Badak LNG
platform library owns `app_*` tables) **plus its own `ses_*` tables**.

## Schema

Apply these scripts to the app's Oracle schema, in order:

| Script | Purpose |
| ------ | ------- |
| `../platform/sql/01_ddl.sql` | Platform DDL — all `app_*` tables, sequences, indexes (re-runnable, drops first) |
| `../platform/sql/02_seed_data.sql` | Platform seed — roles, admin users, pages, menus, grants, parameters, catalogs |
| `00_install_ses_all_monolith.sql` | Monolithic complete installer containing initial platform and SES baseline tables and seeds. |
| `01_app_file_alter.sql` | Extends platform's `app_file` (generic BLOB storage used by every module's uploads) with SES's audit columns — `ALTER TABLE ADD`, guarded/re-runnable, **never drops or recreates the table** |
| `02_approval_ddl.sql` | SES approval DDL — `ses_approval_*`, `ses_form*`, `ses_user_ext` tables and sequences (re-runnable, drops first) |
| `03_request_ddl.sql` | SES request module DDL — `ses_request*`, `ses_zone`, `ses_request_type*`, `ses_doc_requirement` (re-runnable, drops first) |
| `04_seed.sql` | Shared seed — registers `app_page` rows **19-32** for every SES screen, the "New Request" `app_menu` entry, and request-module master data (types, document requirements, zones). |
| `05_dev_seed.sql` | **DEV-ONLY** — small `APP_POSITION_VIEW` snapshot so dynamic approval resolution works locally. **Never run against production**. |
| `06_admin_rbac_seed.sql` | Grants `app_role_page` (all actions) and `app_role_menu` for SES pages 19-32 and the "New Request" menu (id 14) to the `ADMIN` and `SYSADM` roles. |
| `07_menu_seed.sql` | Adds `app_menu` entries skipped by 04 — Approvals, Approval Chains, Approval Form Mapping, Contract Master, Request Type Master (ids 15-19) — and grants them to `ADMIN`/`SYSADM`. |
| `08_payment_ddl.sql` | SES payment module DDL — `ses_request_payment`, `ses_request_payment_history` and sequences. |
| `09_payment_seed.sql` | Registers `app_page` rows **34-37** for payment screens and menu entries under SES and Form & Approval (Admin). |
| `10_payment_admin_rbac_seed.sql` | Grants `app_role_page` and `app_role_menu` for payment screens to `ADMIN`/`SYSADM` and self-service read/submit to all roles. |
| `11_request_worker_alter.sql` | Extends `ses_request_worker` with `badge`/`department`/`section` columns. |
| `12_payment_amount_alter.sql` | Adds `payment_amount` to `ses_request_type` and `paid_amount`/`amount` to `ses_request_payment`. |
| `13_payment_recap_seed.sql` | Registers `app_page` row **38** for Cash Inflow Recap (`/payment/recap`) and menu id 24. |
| `14_dev_seed_app_user.sql` | **DEV-ONLY** — seeds `APP_USER` with snapshot accounts for local test authentication. |
| `15_scheduling_ddl.sql` | SES scheduling module DDL — `ses_request_schedule`, `ses_request_schedule_history` and sequences. |
| `16_scheduling_seed.sql` | Registers `app_page` rows **39-41** and menu entries for scheduling screens. |
| `17_scheduling_rbac_seed.sql` | Grants RBAC for scheduling queue to `ADMIN`/`SYSADM` and read-only calendar access to all roles. |
| `18_scheduling_seed_reset.sql` | **One-off corrective script** resetting early scheduling pages/menus before calendar-for-all rework. |
| `19_requestor_role_seed.sql` | Seeds `REQUESTOR` role (id 4) and baseline self-service grants on pages 27/28. |
| `20_approval_chain_seed.sql` | Seeds baseline approval chains `DEFAULT` and `PAYMENT` with 3-step checkpoints. |
| `21_request_draft_status_alter.sql` | Adds `'DRAFT'` to `ses_request.ck_ses_request_status`. |
| `22_emp_nik_view_dev_seed.sql` | **INACTIVE** — reference snapshot for `EMP_NIK_VIEW` shape. |
| `23_worker_lookup_view.sql` | Creates/refreshes `APP_PERSON` and `SES_WORKER_LOOKUP_VIEW` from unified employee/mitra sources. |
| `23b_worker_lookup_view_ellprd_snapshot_2026-09-18.sql` | **REFERENCE SNAPSHOT ONLY** — historical ELLPRD view definition captured on 2026-09-18. |
| `24_contract_v_dev_seed.sql` | **DEV-ONLY** — local snapshot table `CONTRACT_V` for offline development. |
| `25_payment_status_cancelled_alter.sql` | Adds `'CANCELLED'` to `ses_request_payment.ck_ses_payment_status`. |
| `26_payment_generalize_alter.sql` | Widens `ses_request_payment` from request-only FK to generic `(business_type, business_ref_id)` pointer. |
| `27_sticker_ddl.sql` | SES vehicle sticker module DDL — `ses_sticker_type`, `ses_sticker_request`, `ses_sticker_vehicle`. |
| `28_sticker_seed.sql` | Registers `app_page` rows **42-45** and menu entries for sticker screens. |
| `29_dashboard_history_seed.sql` | Registers `app_page` rows **46-47** for `/history` and `/dashboard` screens. |
| `30_menu_reorg_rbac_sync_seed.sql` | Synchronizes menu reorganization, "SES (Admin)", "SES Form Configuration", and RBAC grants. |
| `31_align_app_tables_with_kpi.sql` | Widens platform columns (`APP_USER`, `APP_FILE`, etc.) to align with KPI schema. |
| `32_app_person_view.sql` | Standalone view for `APP_PERSON`. |
| `33_contract_v_use_carsis_view.sql` | Replaces local `CONTRACT_V` table with clean deduplicated view over `CARSIS.CONTRACT_V`. |
| `34_app_user_cleanse_from_emp_info_view.sql` | Cleanses `app_user` full names, emails, and positions against `APP_EMP_INFO_VIEW`. |
| `35_ses_approval_pending_view.sql` | Creates `SES_APPROVAL_PENDING_VIEW` with PJS position resolution. |
| `36_app_approval_view_ses.sql` | Replaces `APP_APPROVAL_VIEW` with SES-specific pending approval definition. |
| `37_mail_parameter_seed.sql` | Seeds outbound email configuration parameters into `app_parameter`. |
| `39_app_link_production.sql` | Configures `APP_LINK` parameter to deployed production URL. |
| `40_dashboard_admin_view_grant.sql` | Grants `SUBMIT` action on `/dashboard` to `ADMIN`/`SYSADM` for org-wide view. |
| `41_history_admin_view_grant.sql` | Grants `SUBMIT` action on `/history` to `ADMIN`/`SYSADM` for org-wide history. |
| `42_repair_missing_sequences.sql` | Repairs and verifies all missing Oracle sequences across the SES schema. |
| `47_app_position_view_pjs_date_filter.sql` | Filters active dates for PJS holders in `APP_POSITION_VIEW`. |
| `48_mgr_approval_and_auth_type_alter.sql` | Adds manager approval flags and `AUTH_TYPE` authentication column. |
| `49_cash_expense_ddl.sql` | DDL for petty cash table `SES_CASH_EXPENSE`. |
| `50_cash_expense_seed.sql` | Pages, menus, and RBAC grants for Cash Expense module. |
| `51_orphan_app_file_cleanup.sql` | Cleans up orphaned rows in `APP_FILE`. |
| `52_hybrid_routing_alter.sql` | Adds hybrid routing columns (`STEP_ROLE`, `PARALLEL_GROUP`, `COMPLETION_RULE`). |
| `53_signoff_and_signature_ddl.sql` | Legacy signoff DDL script. |
| `53_task_signoff_ddl.sql` | Production signoff table `SES_APPROVAL_TASK_SIGNOFF`. |
| `54_request_evaluator_ddl.sql` | DDL for request evaluator table `SES_REQUEST_EVALUATOR`. |
| `55_approval_pending_view_parallel.sql` | Parallel approval support in `SES_APPROVAL_PENDING_VIEW`. |
| `56_hybrid_chain_seed.sql` | Baseline seed for hybrid approval chains. |
| `56b_adhoc_payment_chain_quickfix_2026-10-06.sql` | Quickfix for payment approval chain step ordering and task links. |
| `57_hybrid_chain_reshape_forced.sql` | Restructures hybrid approval chains to target topology. |
| `58_procurement_supervisor_role_alter.sql` | Adds `PROCUREMENT_SUPERVISOR` role and step mapping. |
| `60_badge_chain_reshape_v2.sql` | Reshapes Badge approval chains to standardized 7-stage pipeline. |
| `61_request_email_and_upload_token_alter.sql` | Adds contractor recipient email, upload token, and `WAITING_FOR_UPLOAD` status. |
| `62_cleanup_goods_module.sql` | Cleans up obsolete goods/vehicle experimental tables. |
| `62_qa_reset_transactional_data.sql` | QA utility script to reset transactional data for testing. |
| `63_badge_naming_alignment.sql` | Aligns badge module naming and chain key conventions (`BADGE_*`). |
| `64_ses_configuration_and_ext_upload.sql` | Adds `NEED_EXT_PAGE_UPLOAD` flag to request types and config menu. |
| `65_ext_upload_staging_tables.sql` | DDL for contractor document upload staging tables (`SES_EXT_UPLOAD_*`). |
| `66_paid_types_to_payment_chain.sql` | Routes paid badge request types to `BADGE_PAYMENT` chain. |
| `67_worker_screening_status.sql` | Adds `SCREENING_STATUS` column and indexes on `SES_REQUEST_WORKER`. |
| `68_set_app_link_local.sql` | Local development environment URL configuration. |
| `69_app_link_ext_seed.sql` | Seeds `APP_LINK_EXT` parameter for external contractor upload portal. |
| `70_worker_passport_support.sql` | Extends `NIK` column to VARCHAR2(30) and adds `ID_TYPE` for passport support. |
| `71_worker_screening_columns.sql` | Adds `SCREENING_DATE`, `SCREENING_REASON`, and check constraints for screening status. |
| `72_riwayat_user_access_grant.sql` | Promotes Riwayat menu to top-level and grants read access. |
| `73_history_print_security_grant.sql` | Grants print permohonan permission on history to security personnel. |
| `74_officer_checkpoint_rbac_grant.sql` | Grants checkpoint operations on `/scheduling` and `/payment` to `SECURITY` role. |
| `80_app_link_ext.sql` | Configures production `APP_LINK_EXT` URL for external document upload portal. |
| `81_ses_text_parameter_seed.sql` | Seeds user-facing notification, warning, and disclaimer text into `APP_PARAMETER` (`SES_TXT_*`). |
| `82_ses_history_view.sql` | Baseline database view `SES_HISTORY_V` for unified cross-module audit and search. |
| `90_security_rbac_align.sql` | Aligns `SECURITY` role page grants with ELLDEV standard (10 page grants, no direct menu). |
| `91_sticker_request_type_code.sql` | Adds `STICKER_TYPE_CODE`, `CONTRACT_NO`, `CONTRACT_EXPIRY_DATE`, `REQUESTOR_TYPE` to `SES_STICKER_REQUEST` and creates `SES_STICKER_VEH_DOC` table & sequence. |
| `92_performance_indexes.sql` | Performance indexes on `APP_USER`, `SES_REQUEST`, and `SES_STICKER_REQUEST` for fast sorting & filtering. |
| `93_history_stage_resolution_view.sql` | Resolves active checkpoints in `SES_HISTORY_V` for accurate workflow stage tracking. |
| `94_platform_147_and_payment_index.sql` | Platform 1.4.7 compatibility (`APP_JOB.ACTIVE_FLAG`) and `IDX_SES_PAYMENT_STAT_ID` for officer queue. |

## Approval module conventions

Chains, steps, and assignees are **fully GUI/DB-driven** — nothing about
them is code-defined. Administrators create/rename/activate/delete chains,
add/rename/reorder/delete steps, and put many assignees on one step
(mixing fixed positions, fixed users, and the dynamic requestor-manager
type) from `/approval/chain`. A step's assignees resolve **ANY** /
first-come-first-serve — whoever acts first closes the step; there is no
quorum mode.

**Convention (decided 2026-09-16): assign by `POSITION`, not `USER`.** When
configuring a step's assignees, always pick `POSITION` (resolved live off
`APP_POSITION_VIEW` at task-creation time — see `ApprovalResolverService`)
and never `USER` (a fixed `APP_USER` row). Pick the position from
`APP_POSITION_LKP_VIEW` (357 rows: `POSITION_ID`/`POS_TITLE`/`TOTAL_PERSON`
— a plain lookup, no co-holder duplication) rather than the richer
`APP_POSITION_VIEW` (587 rows, one row per co-holder) when just populating a
picker. **Why:** a position survives personnel changes (promotion,
transfer, resignation) without anyone having to remember to update every
chain that referenced the old holder by `USER`; a fixed `USER` assignee
silently keeps pointing at someone who may no longer hold that
responsibility.

This is a **policy for new configuration only** — no code change. `USER`
remains fully supported (`AssigneeType.USER`,
`ApprovalResolverService.resolveUser()`): 7 existing `ses_approval_assignee`
rows use it today (the seeded `DEFAULT`/`PAYMENT` chains' admin-approval
steps, `sql/20_approval_chain_seed.sql`) and are left as-is. Reconfiguring
those 7 to `POSITION` is tracked as technical debt, not a blocker — do it
opportunistically from `/approval/chain` when convenient, no rush.

A form is bound to a chain through `ses_form_approval_map`, keyed by a
**form-owned** `form_code` + `match_key` pair — never an enum value the
approval module has to interpret. A form module registers itself by
publishing one `ApprovalFormDescriptor` bean (`com.badaklng.approval.spi`);
the approval module has zero compile-time dependency on any form module.
`match_key` options (the routing dropdown) are read live from the form
module's **own** table via `descriptor.matchKeys()` — the approval module
stores no business vocabulary of its own.

`ses_approval_step.uq_ses_approval_step` is `DEFERRABLE INITIALLY
DEFERRED`: reordering steps renumbers every step in one transaction and
passes through illegal intermediate duplicates before the final state is
reached, so the uniqueness check is deferred to `COMMIT`. Supported since
Oracle 8i. If a DBA disallows deferrable constraints, the documented
fallback (see the DDL comment on that constraint) is a two-phase
negative-offset renumber.

**In-flight instances are frozen at trigger time**, including the
assignee *definitions* of every not-yet-reached step
(`ses_approval_task_assignee_def`) — later chain edits, including step
deletion, never affect a running instance. Person/holder resolution still
happens at each step's activation time against that frozen configuration.

**Manual RBAC re-grant required.** Chain editing used to live at
`/approval/form` (shared with approve/reject); it has moved to
`/approval/chain` + `/approval/chain/form` so approvers and chain
administrators can be granted independently. Any environment that
previously granted `/approval/form` for chain editing must be re-granted
on the new URLs.

## Upload convention

All file uploads across the whole app — approval attachments, request
module worker documents, proof of payment — go through the shared
`app_file` table via `AppFileService` (`com.badaklng.ses.file`). Callers
never touch `app_file` directly; `ref_id` follows the convention
`<OWNER_TABLE>:<pk>` (e.g. `SES_REQUEST_WORKER_DOC:412`) so a file's
owning row can always be resolved for the ownership check in
`GET /file/{fileId}`. Platform's own `AppFile` entity is intentionally
unused inside `ses` — it only maps the original six columns platform
created; `SesAppFile` maps the full ten-column shape after
`01_app_file_alter.sql` has run.

`app_file` is the one table SES doesn't create — platform owns creation
(`platform/sql/01_ddl.sql`), SES only extends it with an `ALTER TABLE ADD`
for the audit columns (`01_app_file_alter.sql`). This is deliberately
**not** a drop-and-recreate: dropping would destroy any BLOB data already
in the table. The alter is guarded (checks `all_tab_columns` first) so
it's safe to re-run.

## SES-owned tables

All SES tables are prefixed `ses_` and are owned by this app. Foreign-key
references to `APP_USER` and `APP_POSITION_VIEW` are intentionally NOT
database FKs: `APP_USER` lives in the platform schema, and
`APP_POSITION_VIEW` is a read-only view in production. Integrity is
enforced in the application layer. The same convention extends to
`app_file`: `ref_id` and `payment_file_id` / worker-doc `file_id` columns
are plain pointers, not FKs.

| Table | Purpose |
| ----- | ------- |
| `ses_approval_chain` | One row per approval chain — GUI-managed, guarded delete |
| `ses_approval_step` | Ordered steps of a chain — GUI-managed (add/rename/reorder/delete) |
| `ses_approval_assignee` | Who may approve a step — many per step, ANY / first-come-first-serve |
| `ses_form` | Registry of form modules using the approval engine (descriptor-synced) |
| `ses_form_approval_map` | Binds a form's default chain + per-match_key overrides |
| `ses_approval_instance` | One approval process per business submission |
| `ses_approval_task` | Per-instance step snapshot (frozen at trigger time) |
| `ses_approval_task_assignee_def` | Per-task snapshot of a step's assignee *definitions* (the freeze that protects in-flight instances from later chain edits) |
| `ses_approval_task_assignee` | Resolved assignees of a task (frozen) |
| `ses_approval_history` | Append-only audit trail |
| `ses_user_ext` | SES extension of `app_user` (holds `report_to_pos_id`) |
| `ses_request_type` | "Types of Request" — also the approval routing dimension for the request module |
| `ses_doc_requirement` | Master list of document kinds a worker may need to upload |
| `ses_request_type_doc` | Which documents a request type demands per worker |
| `ses_zone` | "Zone Access" options |
| `ses_request` | One row per submitted request |
| `ses_request_worker` | Worker Data table rows |
| `ses_request_worker_doc` | One uploaded document per (worker, document requirement) |
| `ses_request_payment` | Current-state payment/proof-of-payment item per business record — soft `(business_type, business_ref_id)` pointer, not an FK (owned by `com.badaklng.payment`, generalized across modules by `26_payment_generalize_alter.sql`) |
| `ses_request_payment_history` | Append-only submit/approve/reject log per payment item, backs the detail page's history card |
| `ses_request_schedule` | Current-state scheduling item per (request, schedule type) — one mutable row for its whole lifecycle (owned by `com.badaklng.scheduling`) |
| `ses_request_schedule_history` | Append-only create/reschedule/cancel/attend/no-show log per schedule item, backs the manage page's history card |
| `ses_sticker_type` | "Jenis Sticker" master — each type carries its own price (owned by `com.badaklng.sticker`) |
| `ses_sticker_request` | One row per submitted vehicle sticker request |
| `ses_sticker_vehicle` | "Data Kendaraan" table rows — one per vehicle on a sticker request, each with its own optional "lampiran surat" upload |
