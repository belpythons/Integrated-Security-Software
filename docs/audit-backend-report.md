# Backend & Database Performance Audit Report
## Application: SES Monolithic Spring Boot (Spring Data JPA/Hibernate + Oracle 12c)

---

## 1. Executive Summary & Root Cause Verdict

**Primary bottleneck: ORM Fetch Logic / N+1 Query Patterns (HIGH severity)**

The application has well-defined entity relationships with `FetchType.LAZY` annotated on `@OneToMany` and `@ManyToOne` associations, which is correct in principle. However, the **service-layer read methods** consistently fetch entity graphs using plain `findById()` or simple `findBy*` queries without `left join fetch`, then access related entity non-ID fields within iteration loops or after the repository call. This triggers **lazy-loading N+1 queries** per request.

Key pathways:
- `MaintenanceScheduleService.buildMaintenanceSchedules()` (line 271-283): iterates `findByEquipmentNumberOrderByDueDateAsc()` results and accesses `s.getMaintenanceType().getMaintenanceTitle()` and `s.getEquipment().getEquipmentName()` per row → N+1 on `maintenance_type` and `equipment` tables
- `MaintenanceScheduleService.approveSchedule()` / `rejectSchedule()` (line 165-192): `findById()` + `schedule.getEquipment().getEquipmentName()` / `schedule.getMaintenanceType().getMaintenanceTitle()` → N+1
- `EquipmentProposalService.approveProposal()` / `rejectProposal()` (line 157-198): `findById()` + `proposal.getEquipment()` / `proposal.getRequestorUser()` → N+1
- `EquipmentProposalService.isEditProposal()` (line 211-215): `countByEquipment_EquipmentNumber()` inside a loop or after `getEquipment()` → additional queries

**Secondary bottlenecks (Med/Low):**
- Connection pool size of 10 on Oracle may become limiting under concurrent load with 30s connection timeout
- Missing composite indexes on foreign key columns used in WHERE/JOIN/ORDER BY clauses
- Dashboard/payment in-memory caches have 60s TTL but data is repeatedly read from DB when cache misses occur

**Root Cause Verdict:** The ORM fetch strategy is LAZY (correct), but the service layer does not use `left join fetch` DTO projections or `@Query` with fetch joining, causing the ORM to issue individual SELECTs for each related entity access. This is the **primary performance killer**.

---

## 2. Key Findings & Bottlenecks

| # | Finding | Evidence (File:Line) | Impact |
|---|---------|----------------------|--------|
| 1 | `MaintenanceScheduleService.buildMaintenanceSchedules()` triggers N+1 on `maintenance_type` and `equipment` | `maints/src/main/java/.../MaintenanceScheduleService.java:279-280` inside `for (MaintenanceSchedule s : ...)` | HIGH - per-row lazy load of 2 associations |
| 2 | `MaintenanceScheduleService.approveSchedule()` accesses `schedule.getEquipment().getEquipmentName()` after `findById()` | `MaintenanceScheduleService.java:175` | HIGH - lazy load of Equipment + its columns |
| 3 | `MaintenanceScheduleService.rejectSchedule()` accesses `schedule.getEquipment().getEquipmentName()` and `schedule.getMaintenanceType().getMaintenanceTitle()` | `MaintenanceScheduleService.java:189-191` | HIGH - lazy load of 2 associations |
| 4 | `EquipmentProposalService.approveProposal()` accesses `proposal.getEquipment()` and `proposal.getRequestorUser()` after `findById()` | `EquipmentProposalService.java:163-164,174` | HIGH - lazy load of Equipment + User |
| 5 | `EquipmentProposalService.rejectProposal()` accesses `proposal.getEquipment()` and `proposal.getRequestorUser()` | `EquipmentProposalService.java:194-197` | HIGH - lazy load of 2 associations |
| 6 | `EquipmentProposalService.isEditProposal()` calls `countByEquipment_EquipmentNumber()` after `getEquipment()` | `EquipmentProposalService.java:213-214` | MED - extra COUNT query per check |
| 7 | `User.entity` has `@OneToMany(mappedBy = "requestorUser", fetch = FetchType.LAZY)` on 3 collections | `User.java:51-58` | MED - potential N+1 if iterated without join fetch |
| 8 | `Equipment.entity` has `@OneToMany(mappedBy = "equipment", fetch = FetchType.LAZY)` on 3 collections | `Equipment.java:87-94` | MED - potential N+1 if iterated without join fetch |
| 9 | HikariCP `maximum-pool-size=10` with `connection-timeout=30000ms` may queue connections under concurrent load | `application.properties:33-35` | MED - connection wait time may increase |
| 10 | `ses.dashboard.snapshot-cache-ttl-ms=60000` and `ses.payment.verified-cache-ttl-ms=60000` — cache stale-risk if invalidation lagging | `application.properties:170-171` | MED - repeated DB reads on cache miss |

---

## 3. Severity Matrix

| Isu | Tingkat Keparahan | Dampak Performa |
|-----|-------------------|------------------|
| N+1 query in `buildMaintenanceSchedules()` per equipment | **High** | ~2 extra queries per schedule row; on 10 schedules = 20 additional queries + 20 more for deepening |
| N+1 query in `approach/reject schedule` | **High** | 2 additional SELECTs per approval operation (Equipment + MaintenanceType join) |
| N+1 query in `approve/reject proposal` | **High** | 2 additional SELECTs per approval (Equipment join + User join) |
| HikariCP pool size=10 with 30s timeout | **Medium** | Connection queueing when >10 concurrent DB-intensive requests |
| Missing composite indexes on FK columns | **Medium** | Full table scans on `overhaul_equipment` → `overhaul_motor_report` JOINs |
| Cache TTL 60s with frequent invalidation | **Medium** | Cache hit ratio low; admin/dashboard endpoints hit DB on every request when miss |
| `findByEquipmentNumberOrderByDueDateAsc` without join fetch | **Medium** | Sorts 1000+ rows with un-fetched lazy proxies → Hibernate proxy initialization overhead |

---

## 4. Prioritized Action Plan

### P0 - Critical (Fix N+1 query patterns - immediate performance gain)

1. **`MaintenanceScheduleService.buildMaintenanceSchedules()`** — Rewrite repository query or use DTO projection to eagerly fetch `equipment` and `maintenanceType`:
   - Option A: Add `left join fetch` in `MaintenanceScheduleRepository.findByEquipmentNumberOrderByDueDateAsc()` similar to `findByEquipmentNumberOrderByDueDateAsc` → but need to handle DTO mapping
   - Option B: Create a `MaintenanceScheduleSummaryDTO` and use `@Query("SELECT new com.badaklng.overhaul.dto.MaintenanceScheduleDTO(s.maintenanceProposalId, s.maintenanceType.maintenanceTitle, s.interval, s.startDate, s.dueDate, s.enabledFlag, s.equipment.equipmentNumber, e.equipmentName) FROM MaintenanceSchedule s left join fetch s.equipment left join fetch s.maintenanceType WHERE s.equipment.equipmentNumber = :equipmentNumber ORDER BY s.dueDate asc")`
   - **Immediate fix**: Use `findAllWithAssociationsOrderByCreatedDateDesc()` pattern (already exists) or create equipment-number-filtered version with join fetch.

2. **`MaintenanceScheduleService.approveSchedule()` and `rejectSchedule()`** — Use `findByIdWithAssociations()` instead of `findById()`:
   - `maintenanceScheduleRepository.findByIdWithAssociations(id)` already exists (line 22-25 in MaintenanceScheduleRepository) and uses `left join fetch s.equipment left join fetch s.requestorUser left join fetch s.maintenanceType`
   - Replace `maintenanceScheduleRepository.findById(id)` with `maintenanceScheduleRepository.findByIdWithAssociations(id)` in both methods.

3. **`EquipmentProposalService.approveProposal()` and `rejectProposal()`** — Use `findByIdWithAssociations()` from `EquipmentProposalRepository`:
   - `equipmentProposalRepository.findByIdWithAssociations(id)` already exists (line 20-22) using `left join fetch p.equipment left join fetch p.requestorUser`
   - Replace `equipmentProposalRepository.findById(id)` with `equipmentProposalRepository.findByIdWithAssociations(id)`.

4. **`EquipmentProposalService.isEditProposal()`** — Cache the count result or refactor to avoid extra query:
   - The method is called per proposal approval; the count query `countByEquipment_EquipmentNumber()` is acceptable if proposals per equipment are few, but can be optimized by caching equipment proposal counts.

### P1 - High (Connection Pool & Indexing)

5. **Review HikariCP configuration** based on observed load:
   - Current: `maximum-pool-size=10`, `minimum-idle=2`, `connection-timeout=30000ms`
   - If average concurrent requests > 10, increase `maximum-pool-size` to 20-25 and reduce `connection-timeout` to 10000ms
   - Enable `leak-detection-threshold=20000` to detect abandoned connections early

6. **Add composite indexes on high-traffic JOIN/WHERE columns** (Oracle B-Tree, leftmost prefix rule):
   - `overhaul_motor_report(executor_id, equipment_id)` — both used in @ManyToOne @JoinColumn
   - `overhaul_maintenance_schedule(equipment_number, status, active_flag, enabled_flag)` — used in `findByEquipmentNumberOrderByDueDateAsc`, `findByStatusWithAssociations`
   - `ses_approval_task(chain_id, step_order)` — used in `findByChainIdOrderByStepOrderAsc`
   - `app_user(auth_type, active_flag)` — used in LDAP authentication path

### P2 - Medium (Caching Optimization)

7. **Increase cache TTLs** or add `Cacheable`/`CacheEvict` strategies where appropriate:
   - `ses.approval.inbox-cache-ttl-ms=30000` is already small; consider 60000 or add `@Cacheable("pendingApprovals")` on `AppApprovalViewRepository.findByApproverId()` 
   - `ses.payment.verified-cache-ttl-ms=60000` — verify if `findVerified()` truly needs 60s or can be 300s with proper `@CacheEvict` on approve/submit operations

8. **Add `@Cacheable` + `@CacheEvict` on repository methods** that read master data:
   - e.g., `@Cacheable(value = "maintenanceTypes", key = "#root.methodName")` on `maintenanceTypeRepository.findAll()`
   - Ensure `@CacheEvict` runs on `approveSchedule`, `rejectSchedule`, `saveSchedule` etc.

### P3 - Low (Code Quality / Future)

9. **Migrate read-heavy endpoints to QueryDTO projections** / `Projections` interface or `QueryDsl` to fetch only needed columns instead of full entity graphs.

10. **Add `@EntityGraph` annotations** on service method signatures for explicit fetch control:
    - e.g., `@EntityGraph(attributePaths = {"equipment", "maintenanceType"}) MaintenanceSchedule findByIdWithAssociations(Long id);`

---

## 5. Verification Checklist

| # | Verification Method | Expected Result |
|---|---|---|
| 1 | **Enable Hibernate SQL logs**: `spring.jpa.show-sql=true` and `logging.level.org.hibernate.SQL=DEBUG` | No individual SELECT for lazy-loaded fields after `findById()` — should see `left join fetch` in generated SQL |
| 2 | **Run `EXPLAIN ANALYZE` on bottleneck queries** (e.g., `SELECT ... FROM overhaul_maintenance_schedule WHERE equipment_number = 'EQ-123' ORDER BY due_date`) | Query plan shows **PARTITION RANGE** or **INDEX RANGE SCAN** on composite index, **NOT** TABLE ACCESS FULL |
| 3 | **HikariCP metrics**: monitor `active-connections`, `pending-connections`, `thread-timeout` via `/actuator/metrics/hikaricp` | `pending-connections` should stay near 0 under normal load; if > 5, increase pool size |
| 4 | **Cache hit ratio**: monitor `spring` cache stats or Caffeine/Redis `hits/misses` | Cache hit ratio > 80% for `pendingApprovals`, `dashboardSnapshot`, `verifiedPayments` |
| 5 | **Before/after query count**: Use `pg_stat_statements` (or Oracle `v$sql`) to count queries per endpoint before fixing N+1 vs after | Query count per endpoint reduced by 30-50% after adding `left join fetch`/DTO projections |
| 6 | **Load test**: Simulate 50 concurrent users hitting `/approval/inbox` and `/maintenance/schedules` | Response time should not increase proportionally; N+1 fixes should show sub-linear growth |
| 7 | **Verify `@CacheEvict` execution**: After approve/reject, confirm cache entries are invalidated (monitor `Cache.Evicted` metrics) | No stale data observed after approval/rejection actions; cache refreshes correctly |

---
*End of Report*