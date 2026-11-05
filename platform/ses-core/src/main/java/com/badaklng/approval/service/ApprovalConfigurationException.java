package com.badaklng.approval.service;

/**
 * Thrown when a form/chain configuration cannot resolve a runnable approval
 * chain (form not registered, no default mapping, chain inactive/missing
 * steps, ...). Uncaught, this becomes a 500 page + APP_ERROR row + admin
 * mail via platform's GlobalExceptionHandler — no special wiring needed.
 */
public class ApprovalConfigurationException extends RuntimeException {

    public ApprovalConfigurationException(String message) {
        super(message);
    }
}
