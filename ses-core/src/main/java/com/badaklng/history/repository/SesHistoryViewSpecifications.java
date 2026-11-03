package com.badaklng.history.repository;

import com.badaklng.history.entity.SesHistoryView;
import com.badaklng.ses.jdbc.InChunks;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds the {@code /history} and dashboard scoped query as a {@link
 * Specification} instead of the static JPQL {@code searchScoped} this
 * replaces.
 *
 * <p>The old query bound {@code requestorUserId IN :allowed} straight from a
 * department's whole username set. {@code UserScopeService} falls back to a
 * department-wide scope whenever a user's section is blank, and a department
 * at this company can hold more than Oracle's 1000-expression {@code IN}
 * limit — ORA-01795, surfaced to users as a dashboard 500 (see the incident
 * dated 2026-10-27). {@link InChunks} splits {@code allowed}/{@code involved}
 * into Oracle-safe chunks, each contributed as its own {@code IN}, OR-ed
 * together — exactly what a single chunked query cannot do, since this path
 * is paginated (count + sorted/limited content) and chunk-then-merge only
 * works for unpaginated {@code findAllById}-style reads.
 */
public final class SesHistoryViewSpecifications {

    private SesHistoryViewSpecifications() {
    }

    public static Specification<SesHistoryView> scoped(String term, String status, String module,
                                                        Set<String> allowed, Set<Long> involved) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (term != null && !term.trim().isEmpty()) {
                String like = "%" + term.trim().toUpperCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.upper(root.get("transactionNo")), like),
                        cb.like(cb.upper(root.get("moduleName")), like),
                        cb.like(cb.upper(root.get("requestorUserId")), like),
                        cb.like(cb.upper(root.get("status")), like)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (module != null) {
                predicates.add(cb.equal(root.get("businessType"), module));
            }

            List<Predicate> scope = new ArrayList<>();
            scope.add(cb.equal(cb.upper(root.get("status")), "APPROVED"));
            for (List<String> chunk : InChunks.of(allowed)) {
                scope.add(root.get("requestorUserId").in(chunk));
            }
            for (List<Long> chunk : InChunks.of(involved)) {
                scope.add(root.get("approvalInstanceId").in(chunk));
            }
            predicates.add(cb.or(scope.toArray(new Predicate[0])));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
