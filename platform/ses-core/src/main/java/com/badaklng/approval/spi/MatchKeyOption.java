package com.badaklng.approval.spi;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Immutable (code, label) pair crossing the SPI boundary between the
 * approval module and a form module's own routing table.
 */
@Getter
@RequiredArgsConstructor
public class MatchKeyOption {
    private final String code;
    private final String label;
}
