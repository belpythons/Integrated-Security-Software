package com.badaklng.history.service;

import com.badaklng.history.model.HistoryRow;
import com.badaklng.history.spi.HistorySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock
    private ObjectProvider<HistorySource> historySources;

    @Mock
    private HistorySource historySource;

    @InjectMocks
    private HistoryService historyService;

    @Test
    @DisplayName("scopedRowsSorted filters rows matching allowed usernames from section or department")
    void testScopedRowsSorted() {
        HistoryRow row1 = new HistoryRow(
                "SES_REQUEST", "Badge", "REQ-001", "313588",
                "User Satu", "APPROVED", new Date(1000000L),
                "/view/1", "/doc/1"
        );
        HistoryRow row2 = new HistoryRow(
                "SES_REQUEST", "Badge", "REQ-002", "999999",
                "User Dua", "PENDING", new Date(2000000L),
                "/view/2", null
        );

        when(historySources.stream()).thenReturn(Stream.of(historySource));
        when(historySource.allRows()).thenReturn(Arrays.asList(row1, row2));

        Set<String> scopeUsernames = new HashSet<>(Collections.singletonList("313588"));

        List<HistoryRow> rows = historyService.scopedRowsSorted(scopeUsernames);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTransactionNo()).isEqualTo("REQ-001");
        assertThat(rows.get(0).getRequestorUserId()).isEqualTo("313588");
    }

    @Test
    @DisplayName("scopedRowsSorted with empty allowed set returns empty list without querying sources")
    void testScopedRowsSorted_EmptySet() {
        List<HistoryRow> rows = historyService.scopedRowsSorted(Collections.emptySet());
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("scopedRowsSorted includes approved requests and involved instances")
    void testScopedRowsSorted_InvolvedAndApproved() {
        HistoryRow row1 = new HistoryRow(
                "SES_REQUEST", "Badge", "REQ-001", "other_user",
                "Other User", "APPROVED", new Date(1000000L),
                "/view/1", "/doc/1", 101L
        );
        HistoryRow row2 = new HistoryRow(
                "SES_REQUEST", "Badge", "REQ-002", "other_user2",
                "Other User 2", "PENDING", new Date(2000000L),
                "/view/2", null, 102L
        );
        HistoryRow row3 = new HistoryRow(
                "SES_REQUEST", "Badge", "REQ-003", "other_user3",
                "Other User 3", "PENDING", new Date(3000000L),
                "/view/3", null, 103L
        );

        when(historySources.stream()).thenReturn(Stream.of(historySource));
        when(historySource.allRows()).thenReturn(Arrays.asList(row1, row2, row3));

        // User is involved in row2 (instance 102), row1 is APPROVED, row3 is neither
        Set<Long> involved = Collections.singleton(102L);
        List<HistoryRow> rows = historyService.scopedRowsSorted(Collections.emptySet(), involved);

        assertThat(rows).hasSize(2);
        List<String> reqNos = Arrays.asList(rows.get(0).getTransactionNo(), rows.get(1).getTransactionNo());
        assertThat(reqNos).containsExactly("REQ-002", "REQ-001");
    }
}

