package com.badaklng.approval.service;

import com.badaklng.approval.entity.ApprovalStatus;
import com.badaklng.approval.entity.SesApprovalInstance;
import com.badaklng.approval.entity.HistoryAction;
import com.badaklng.approval.entity.SesApprovalHistory;
import com.badaklng.approval.repository.SesApprovalHistoryRepository;
import com.badaklng.approval.repository.SesApprovalInstanceRepository;
import com.badaklng.approval.entity.SesApprovalTask;
import com.badaklng.approval.entity.SesApprovalTaskCheckpoint;
import com.badaklng.approval.repository.SesApprovalTaskCheckpointRepository;
import com.badaklng.approval.repository.SesApprovalTaskRepository;
import com.badaklng.approval.spi.ApprovalOutcomeListener;
import com.badaklng.approval.spi.CheckpointActionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emails the requestor when their submission finishes (APPROVED/REJECTED),
 * via {@link ApprovalMailService#sendOutcomeEmail}.
 * <p>
 * Reuses the {@link ApprovalOutcomeListener} SPI that {@code ApprovalService}
 * already invokes at every outcome point (approve-completes-chain, reject,
 * checkpoint-reject) — no change to {@code ApprovalService} itself was
 * needed for this notifier, only a new listener bean (see
 * {@link ApprovalRequestorNotifierConfig}).
 * <p>
 * {@code businessRefType()} matches exactly one string per the SPI contract,
 * so one bean per business type is registered (both sharing this same
 * class — see the config) rather than duplicating this logic per module.
 * {@code onCompleted} only receives {@code (businessRefId, finalStatus)},
 * not the note/actor of the action that closed the instance. Rather than
 * widen the SPI for one consumer, this reads them back off the history row
 * that {@code ApprovalService} has already written for that same action —
 * which is why a rejection reason now reaches the requestor at all; before,
 * {@code null, null} was passed and the CATATAN block never rendered.
 */
@RequiredArgsConstructor
public class ApprovalRequestorNotifier implements ApprovalOutcomeListener {

    private final String businessRefType;
    private final SesApprovalInstanceRepository instanceRepository;
    private final SesApprovalHistoryRepository historyRepository;
    private final SesApprovalTaskRepository taskRepository;
    private final SesApprovalTaskCheckpointRepository checkpointRepository;
    private final ApprovalMailService approvalMailService;
    private final ObjectProvider<CheckpointActionProvider> checkpointActionProviders;

    @Override
    public String businessRefType() {
        return businessRefType;
    }

    @Override
    public void onCompleted(Long businessRefId, ApprovalStatus finalStatus) {
        List<SesApprovalInstance> matches = instanceRepository
                .findByBusinessRefTypeAndBusinessRefIdAndStatusOrderByInstanceIdDesc(
                        businessRefType, businessRefId, finalStatus);
        if (matches.isEmpty()) {
            return;
        }
        SesApprovalInstance instance = matches.get(0);
        SesApprovalHistory decision = lastDecision(instance.getInstanceId());

        approvalMailService.sendOutcomeEmail(instance, finalStatus,
                decision != null ? decision.getNote() : null,
                decision != null ? decision.getActorUserId() : null);

        if (finalStatus == ApprovalStatus.APPROVED) {
            sendCompletionEmail(instance);
        }
    }

    /**
     * The "everything is done" mail, separate from the per-approval
     * "disetujui" one above.
     *
     * <p>They answer different questions. The outcome mail says a decision was
     * made; this one says the whole process — pay, screen, collect the badge —
     * is finished, and carries the dates and amounts the requestor actually
     * needs to keep. Sent only when the chain had checkpoints at all: for a
     * chain that is pure approvals (the sticker flow, for one) the outcome
     * mail already said everything there is to say, and a second mail
     * repeating it is just noise.</p>
     *
     * <p>Summary rows come from whichever module owns each checkpoint key, via
     * {@link CheckpointActionProvider#summaryFor} — the approval module stays
     * free of any compile-time knowledge of payments or schedules.</p>
     */
    private void sendCompletionEmail(SesApprovalInstance instance) {
        Map<String, String> summary = new LinkedHashMap<>();
        for (SesApprovalTask task : taskRepository.findByInstanceIdOrderByStepOrderAsc(instance.getInstanceId())) {
            for (SesApprovalTaskCheckpoint cp : checkpointRepository
                    .findByTaskIdOrderByCheckpointIdAsc(task.getTaskId())) {
                checkpointActionProviders.orderedStream()
                        .map(p -> p.summaryFor(cp.getCheckpointKey(), instance.getBusinessRefType(),
                                instance.getBusinessRefId()))
                        .filter(rows -> !rows.isEmpty())
                        .findFirst()
                        .ifPresent(summary::putAll);
            }
        }
        if (summary.isEmpty()) {
            return;
        }
        approvalMailService.sendRequestorInfoEmail(instance, ApprovalStatus.APPROVED,
                "Proses Selesai",
                "Seluruh proses pengajuan Anda telah <b>selesai</b>. Ringkasannya ada di bawah ini.",
                "SELESAI", summary, null, "APPROVAL_PROCESS_DONE");
    }

    /**
     * The last APPROVE/REJECT a person actually performed on this instance,
     * written inside this same transaction before the listeners run.
     *
     * <p>Deliberately not "the last history row": the engine appends its own
     * COMPLETE row after the final approval, whose note is the bookkeeping
     * string "All steps approved; instance completed". Mailing that to the
     * requestor as their reviewer's CATATAN is worse than mailing nothing.</p>
     */
    private SesApprovalHistory lastDecision(Long instanceId) {
        List<SesApprovalHistory> history = historyRepository.findByInstanceIdOrderByHistoryIdAsc(instanceId);
        for (int i = history.size() - 1; i >= 0; i--) {
            HistoryAction action = history.get(i).getAction();
            if (action == HistoryAction.APPROVE || action == HistoryAction.REJECT) {
                return history.get(i);
            }
        }
        return null;
    }
}
