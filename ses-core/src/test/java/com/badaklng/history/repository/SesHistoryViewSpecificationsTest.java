package com.badaklng.history.repository;

import com.badaklng.history.entity.SesHistoryView;
import com.badaklng.ses.jdbc.InChunks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for the 2026-10-27 dashboard incident: a department-wide
 * scope (more usernames than Oracle's 1000-expression IN limit) must not be
 * sent to the database as a single IN list.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SesHistoryViewSpecificationsTest {

    @Mock
    private Root root;
    @Mock
    private CriteriaQuery query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Path path;
    @Mock
    private Predicate predicate;

    @Test
    @DisplayName("scoped() splits an allowed set bigger than InChunks.MAX into several OR-ed IN predicates")
    void chunksLargeAllowedSet() {
        when(root.get(anyString())).thenReturn(path);
        when(cb.upper(any())).thenReturn(path);
        when(cb.equal(any(), any())).thenReturn(predicate);
        when(cb.like(any(), anyString())).thenReturn(predicate);
        when(path.in(any(Collection.class))).thenReturn(predicate);
        when(cb.or(any(Predicate[].class))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Set<String> allowed = new HashSet<>();
        for (int i = 0; i < InChunks.MAX * 2 + 1; i++) {
            allowed.add("USER" + i);
        }

        Specification<SesHistoryView> spec = SesHistoryViewSpecifications.scoped(
                null, null, null, allowed, null);
        spec.toPredicate(root, query, cb);

        ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
        verify(path, times(3)).in(captor.capture());
        int total = captor.getAllValues().stream().mapToInt(Collection::size).sum();
        assertThat(total).isEqualTo(allowed.size());
        captor.getAllValues().forEach(chunk -> assertThat(chunk.size()).isLessThanOrEqualTo(InChunks.MAX));
    }
}
