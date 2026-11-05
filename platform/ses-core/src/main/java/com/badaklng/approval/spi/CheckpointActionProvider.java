package com.badaklng.approval.spi;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Lets the module that owns a checkpoint key contribute the form for
 * resolving it, so an approver can do the work on the approval detail page
 * instead of navigating away and back.
 *
 * <p>Before this, an approver stuck on a PAYMENT_VERIFICATION or
 * SCREENING_SCHEDULE gate had to leave for {@code /payment/verify/{id}} or
 * {@code /scheduling/manage/{requestId}/{type}}, act there, and return —
 * three page loads to clear one gate, times three gates. The work itself
 * already closed the checkpoint automatically ({@code PaymentService#approve}
 * and {@code SchedulingService#markAttended} both call
 * {@code ApprovalService#completeCheckpointByKey}); only the UI was split.</p>
 *
 * <p>Direction of dependency is the same as every other SPI here
 * ({@link CheckpointTaskType}, {@link ApprovalOutcomeListener}): the business
 * module depends on approval, never the reverse. The approval module renders
 * a fragment it is handed the <em>name</em> of and knows nothing about.</p>
 *
 * <p>Implement as a Spring bean; the approval module discovers every
 * implementation through {@code ObjectProvider<CheckpointActionProvider>}.
 * The first provider returning a non-empty {@link CheckpointAction} for a key
 * wins, so two modules must not claim the same checkpoint key.</p>
 */
public interface CheckpointActionProvider {

    /**
     * The inline form for this checkpoint, or empty when this provider does
     * not own the key, the business record is missing, or there is simply
     * nothing to show. Empty is normal — the approver keeps the manual
     * "Mark Done" / "Skip" buttons either way.
     *
     * @param checkpointKey   {@code SesApprovalStepTask.checkpointKey}
     * @param businessRefType {@code SesApprovalInstance.businessRefType}
     * @param businessRefId   {@code SesApprovalInstance.businessRefId}
     * @param returnTo        path to come back to after the form posts; pass
     *                        it straight through to the action URL
     */
    Optional<CheckpointAction> actionFor(String checkpointKey, String businessRefType,
                                         Long businessRefId, String returnTo);

    /**
     * Label/value rows describing how this checkpoint was actually resolved —
     * the date a screening happened, the amount a payment came in for — for
     * the "process finished" mail the requestor gets at the end.
     *
     * <p>Default empty so a provider that has nothing to add needs no code.
     * Keep it to one or two short rows: the mail body is capped at
     * {@code ApprovalEmailTemplateBuilder.MAX_BODY} and every row here
     * competes with the form's own detail rows for that budget.</p>
     */
    default Map<String, String> summaryFor(String checkpointKey, String businessRefType, Long businessRefId) {
        return Collections.emptyMap();
    }

    /**
     * One inline form: which Thymeleaf fragment renders it, and the values it
     * needs. Template and selector are two fields rather than one
     * {@code "template :: selector"} string for the reason spelled out in
     * {@code ApprovalController#addDetailFragment} — Thymeleaf only splits on
     * {@code ::} when that separator is literally in the including markup.
     */
    class CheckpointAction {

        private final String fragmentTemplate;
        private final String fragmentSelector;
        private final Map<String, Object> model;

        public CheckpointAction(String fragmentTemplate, String fragmentSelector, Map<String, Object> model) {
            this.fragmentTemplate = fragmentTemplate;
            this.fragmentSelector = fragmentSelector;
            this.model = model == null ? Collections.emptyMap() : model;
        }

        public String getFragmentTemplate() {
            return fragmentTemplate;
        }

        public String getFragmentSelector() {
            return fragmentSelector;
        }

        public Map<String, Object> getModel() {
            return model;
        }
    }
}
