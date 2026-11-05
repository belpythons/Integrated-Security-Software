package com.badaklng.approval.model;

import com.badaklng.approval.entity.AssigneeType;
import com.badaklng.approval.entity.CompletionRule;
import com.badaklng.approval.entity.SesApprovalAssignee;
import com.badaklng.approval.entity.SesApprovalChain;
import com.badaklng.approval.entity.SesApprovalStep;
import com.badaklng.approval.entity.SesApprovalStepTask;
import com.badaklng.approval.service.ApprovalChainService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Web binding target for the whole chain editor page (header + steps +
 * nested assignees), posted with Spring indexed binding:
 * {@code steps[i].assignees[j].*}. Both nested row classes need a public
 * no-arg constructor — Spring's WebDataBinder with autoGrowNestedPaths
 * instantiates e.g. {@code steps[3].assignees[2].posId} on demand, which
 * only works with a no-arg constructor.
 *
 * {@link #toServiceInput()} maps this form to
 * {@link ApprovalChainService.ChainEditInput} before it is handed to
 * {@code ApprovalChainService.createOrUpdateHeader} — the web form never
 * binds directly to the service-layer input type.
 *
 * Note the split: this form now carries only the chain HEADER. Steps are
 * saved one at a time over AJAX through {@code ApprovalChainService.saveStep},
 * binding a single {@link StepRow} per request. {@link StepRow#toStepInput()}
 * is shared by both paths so the two can never drift.
 */
@Getter
@Setter
@NoArgsConstructor
public class ApprovalChainEditForm {

    private Long chainId;
    private String chainKey;
    private String chainName;
    private String description;
    private String activeFlag = "Y";
    private List<StepRow> steps = new ArrayList<>();

    /**
     * Populates an edit form from an existing chain, its steps (already in
     * step_order), a step-id -> assignees lookup, and a step-id ->
     * checkpoint (gated task) definitions lookup — both built by the
     * controller from repeated {@code ApprovalChainService.findAssignees}
     * / {@code findStepTasks} calls.
     */
    public static ApprovalChainEditForm from(SesApprovalChain chain, List<SesApprovalStep> steps,
            Map<Long, List<SesApprovalAssignee>> assigneesByStepId,
            Map<Long, List<SesApprovalStepTask>> checkpointsByStepId) {
        ApprovalChainEditForm f = new ApprovalChainEditForm();
        f.setChainId(chain.getChainId());
        f.setChainKey(chain.getChainKey());
        f.setChainName(chain.getChainName());
        f.setDescription(chain.getDescription());
        f.setActiveFlag(chain.getActiveFlag());

        List<StepRow> stepRows = new ArrayList<>();
        if (steps != null) {
            for (SesApprovalStep step : steps) {
                StepRow row = new StepRow();
                row.setStepId(step.getStepId());
                row.setStepName(step.getStepName());
                row.setStepRole(step.getStepRole() == null ? "" : step.getStepRole().name());
                row.setParallelGroup(step.getParallelGroup());
                row.setCompletionRule(step.getCompletionRule() == null
                        ? CompletionRule.ANY.name() : step.getCompletionRule().name());

                List<SesApprovalAssignee> assignees = assigneesByStepId != null
                        ? assigneesByStepId.get(step.getStepId())
                        : null;
                List<AssigneeRow> assigneeRows = new ArrayList<>();
                if (assignees != null) {
                    for (SesApprovalAssignee a : assignees) {
                        AssigneeRow ar = new AssigneeRow();
                        ar.setAssigneeId(a.getAssigneeId());
                        ar.setAssigneeType(a.getAssigneeType());
                        ar.setPosId(a.getPosId());
                        ar.setUserId(a.getUserId());
                        assigneeRows.add(ar);
                    }
                }
                row.setAssignees(assigneeRows);

                List<SesApprovalStepTask> checkpoints = checkpointsByStepId != null
                        ? checkpointsByStepId.get(step.getStepId())
                        : null;
                List<StepTaskRow> taskRows = new ArrayList<>();
                if (checkpoints != null) {
                    for (SesApprovalStepTask t : checkpoints) {
                        StepTaskRow tr = new StepTaskRow();
                        tr.setStepTaskId(t.getStepTaskId());
                        tr.setTaskLabel(t.getTaskLabel());
                        tr.setCheckpointKey(t.getCheckpointKey());
                        taskRows.add(tr);
                    }
                }
                row.setTasks(taskRows);

                stepRows.add(row);
            }
        }
        f.setSteps(stepRows);
        return f;
    }

    /**
     * Maps this web-binding form to the service-layer input shape.
     */
    public ApprovalChainService.ChainEditInput toServiceInput() {
        ApprovalChainService.ChainEditInput input = new ApprovalChainService.ChainEditInput();
        input.setChainId(chainId);
        input.setChainKey(chainKey);
        input.setChainName(chainName);
        input.setDescription(description);
        input.setActiveFlag("Y".equalsIgnoreCase(activeFlag) ? "Y" : "N");

        List<ApprovalChainService.StepInput> stepInputs = new ArrayList<>();
        if (steps != null) {
            for (StepRow row : steps) {
                if (row == null) {
                    continue;
                }
                stepInputs.add(row.toStepInput());
            }
        }
        input.setSteps(stepInputs);
        return input;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class StepRow {
        private Long stepId;
        private String stepName;
        /**
         * {@code SECURITY_STAFF} / {@code REQUESTOR_MANAGER} /
         * {@code PROCUREMENT_MANAGER} / {@code SECURITY_MANAGER}, or empty for
         * an ordinary step. Bound from a {@code <select>}, so "no role" arrives
         * as the empty string and is normalised in
         * {@link ApprovalChainEditForm#toServiceInput()}.
         */
        private String stepRole = "";
        /**
         * Steps sharing a number activate together. Empty means "a group of its
         * own", i.e. serial.
         *
         * <p>Carried through the form even though most admins never touch it:
         * {@code saveStep} rewrites the whole step row, so a field the modal
         * does not round-trip would be silently reset to null the first time
         * someone edits that step — quietly collapsing a parallel chain back
         * to serial on a rename.</p>
         */
        private Integer parallelGroup;
        /** {@code ANY} (default) or {@code ALL}. Round-tripped for the same reason as {@link #parallelGroup}. */
        private String completionRule = "ANY";
        private List<AssigneeRow> assignees = new ArrayList<>();
        private List<StepTaskRow> tasks = new ArrayList<>();

        /**
         * Maps this one row to the service-layer step input.
         *
         * <p>On {@code StepRow} rather than inline in
         * {@link ApprovalChainEditForm#toServiceInput()} because the modal
         * chain builder binds a SINGLE row — {@code POST /approval/chain/{id}/step/save}
         * posts one step, not the whole chain — and both doors must produce
         * exactly the same shape. Two copies of this mapping would drift.</p>
         *
         * <p>Note {@code posId} and {@code userId} are each cleared unless the
         * chosen type actually uses them. The modal keeps both inputs in the
         * DOM and merely hides the irrelevant one, so both still submit;
         * persisting the stale one would leave a POSITION assignee carrying a
         * user id that nothing reads and everything has to explain.</p>
         */
        public ApprovalChainService.StepInput toStepInput() {
            ApprovalChainService.StepInput stepInput = new ApprovalChainService.StepInput();
            stepInput.setStepId(stepId);
            stepInput.setStepName(stepName);
            stepInput.setStepRole(stepRole);
            stepInput.setParallelGroup(parallelGroup);
            stepInput.setCompletionRule(completionRule);

            List<ApprovalChainService.AssigneeInput> assigneeInputs = new ArrayList<>();
            if (assignees != null) {
                for (AssigneeRow ar : assignees) {
                    if (ar == null || ar.getAssigneeType() == null) {
                        continue;
                    }
                    ApprovalChainService.AssigneeInput assigneeInput = new ApprovalChainService.AssigneeInput();
                    assigneeInput.setAssigneeId(ar.getAssigneeId());
                    assigneeInput.setAssigneeType(ar.getAssigneeType());
                    assigneeInput.setPosId(ar.getAssigneeType() == AssigneeType.POSITION ? ar.getPosId() : null);
                    assigneeInput.setUserId(ar.getAssigneeType() == AssigneeType.USER ? ar.getUserId() : null);
                    assigneeInputs.add(assigneeInput);
                }
            }
            stepInput.setAssignees(assigneeInputs);

            List<ApprovalChainService.StepTaskInput> taskInputs = new ArrayList<>();
            if (tasks != null) {
                for (StepTaskRow tr : tasks) {
                    if (tr == null) {
                        continue;
                    }
                    ApprovalChainService.StepTaskInput taskInput = new ApprovalChainService.StepTaskInput();
                    taskInput.setStepTaskId(tr.getStepTaskId());
                    taskInput.setTaskLabel(tr.getTaskLabel());
                    taskInput.setCheckpointKey(tr.getCheckpointKey());
                    taskInputs.add(taskInput);
                }
            }
            stepInput.setTasks(taskInputs);
            return stepInput;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AssigneeRow {
        private Long assigneeId;
        private AssigneeType assigneeType;
        private String posId;
        private String userId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class StepTaskRow {
        private Long stepTaskId;
        private String taskLabel;
        private String checkpointKey;
    }
}
