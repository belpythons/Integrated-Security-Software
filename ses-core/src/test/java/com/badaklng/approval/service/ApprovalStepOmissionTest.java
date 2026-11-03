package com.badaklng.approval.service;

import com.badaklng.approval.entity.ChainResolutionTier;
import com.badaklng.approval.entity.SesApprovalChain;
import com.badaklng.approval.entity.SesApprovalInstance;
import com.badaklng.approval.entity.SesApprovalStep;
import com.badaklng.approval.entity.SesApprovalTask;
import com.badaklng.approval.entity.StepRole;
import com.badaklng.approval.repository.AppApprovalViewRepository;
import com.badaklng.approval.repository.AppPositionViewRepository;
import com.badaklng.approval.repository.SesApprovalAssigneeRepository;
import com.badaklng.approval.repository.SesApprovalHistoryRepository;
import com.badaklng.approval.repository.SesApprovalInstanceRepository;
import com.badaklng.approval.repository.SesApprovalStepRepository;
import com.badaklng.approval.repository.SesApprovalStepTaskRepository;
import com.badaklng.approval.repository.SesApprovalTaskAssigneeDefRepository;
import com.badaklng.approval.repository.SesApprovalTaskAssigneeRepository;
import com.badaklng.approval.repository.SesApprovalTaskCheckpointRepository;
import com.badaklng.approval.repository.SesApprovalTaskRepository;
import com.badaklng.approval.repository.SesApprovalTaskSignoffRepository;
import com.badaklng.approval.spi.ApprovalOutcomeListener;
import com.badaklng.platform.system.repository.AppUserRepository;
import com.badaklng.ses.user.UserDisplayNameService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that when request type toggles for Manager Pemohon or Procurement
 * are un-checked (disabled), their corresponding tasks are completely omitted
 * during trigger() rather than created as SKIPPED rows.
 */
class ApprovalStepOmissionTest {

    @Test
    @DisplayName("When Manager and Procurement steps are disabled, tasks for those roles are never created")
    void disabledStepsAreOmittedFromTasks() {
        SesApprovalStepRepository stepRepo = mock(SesApprovalStepRepository.class);
        SesApprovalStepTaskRepository stepTaskRepo = mock(SesApprovalStepTaskRepository.class);
        SesApprovalAssigneeRepository assigneeRepo = mock(SesApprovalAssigneeRepository.class);
        SesApprovalInstanceRepository instanceRepo = mock(SesApprovalInstanceRepository.class);
        SesApprovalTaskRepository taskRepo = mock(SesApprovalTaskRepository.class);
        SesApprovalTaskAssigneeRepo(taskRepo);
        SesApprovalTaskAssigneeRepository taskAssigneeRepo = mock(SesApprovalTaskAssigneeRepository.class);
        SesApprovalTaskAssigneeDefRepository taskAssigneeDefRepo = mock(SesApprovalTaskAssigneeDefRepository.class);
        SesApprovalTaskCheckpointRepository checkpointRepo = mock(SesApprovalTaskCheckpointRepository.class);
        SesApprovalTaskSignoffRepository signoffRepo = mock(SesApprovalTaskSignoffRepository.class);
        AppPositionViewRepository posViewRepo = mock(AppPositionViewRepository.class);
        SesApprovalHistoryRepository historyRepo = mock(SesApprovalHistoryRepository.class);
        ApprovalResolverService resolver = mock(ApprovalResolverService.class);
        AppUserRepository appUserRepo = mock(AppUserRepository.class);
        ApprovalFormService formService = mock(ApprovalFormService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalOutcomeListener> outcomeListeners = mock(ObjectProvider.class);
        AppApprovalViewRepository appApprovalViewRepo = mock(AppApprovalViewRepository.class);
        ApprovalMailService mailService = mock(ApprovalMailService.class);
        UserDisplayNameService displayNameService = mock(UserDisplayNameService.class);

        SesApprovalChain chain = new SesApprovalChain();
        chain.setChainId(100L);
        chain.setChainKey("BADGE_DEFAULT");
        chain.setChainName("Badge Default");

        ApprovalFormService.ChainResolution res = new ApprovalFormService.ChainResolution(
                chain, ChainResolutionTier.DEFAULT, "BADGE_DEFAULT");
        when(formService.resolveChain(eq("REQ_BADGE"), any())).thenReturn(res);

        SesApprovalStep stepMgr = new SesApprovalStep();
        stepMgr.setStepId(1L);
        stepMgr.setStepName("Persetujuan Manager Pemohon");
        stepMgr.setStepOrder(1);
        stepMgr.setStepRole(StepRole.REQUESTOR_MANAGER);

        SesApprovalStep stepProc = new SesApprovalStep();
        stepProc.setStepId(2L);
        stepProc.setStepName("Persetujuan Manager Procurement");
        stepProc.setStepOrder(2);
        stepProc.setStepRole(StepRole.PROCUREMENT_MANAGER);

        SesApprovalStep stepSec = new SesApprovalStep();
        stepSec.setStepId(3L);
        stepSec.setStepName("Persetujuan Manager Security");
        stepSec.setStepOrder(3);
        stepSec.setStepRole(StepRole.SECURITY_MANAGER);

        when(stepRepo.findByChainIdOrderByStepOrderAsc(100L))
                .thenReturn(Arrays.asList(stepMgr, stepProc, stepSec));

        when(instanceRepo.save(any())).thenAnswer(inv -> {
            SesApprovalInstance inst = inv.getArgument(0);
            inst.setInstanceId(555L);
            return inst;
        });

        List<SesApprovalTask> savedTasks = new ArrayList<>();
        when(taskRepo.save(any())).thenAnswer(inv -> {
            SesApprovalTask t = inv.getArgument(0);
            t.setTaskId((long) (savedTasks.size() + 1));
            savedTasks.add(t);
            return t;
        });

        when(taskRepo.findByInstanceIdOrderByStepOrderAsc(555L)).thenAnswer(inv -> savedTasks);

        ApprovalService service = new ApprovalService(
                stepRepo, stepTaskRepo, assigneeRepo, instanceRepo, taskRepo,
                taskAssigneeRepo, taskAssigneeDefRepo, checkpointRepo, signoffRepo,
                posViewRepo, historyRepo, resolver, appUserRepo, formService,
                outcomeListeners, appApprovalViewRepo, mailService, displayNameService,
                mock(com.badaklng.request.repository.AppEmpLkpViewRepository.class)
        );

        // Trigger options with BOTH Manager and Procurement disabled
        ApprovalService.TriggerOptions opts = new ApprovalService.TriggerOptions(
                false, false, Collections.emptyList(), false);

        service.trigger("REQ_BADGE", "BADGE_NEW", "REQ", 10L, "309000", opts);

        // Verify that only the Security Manager step task was created!
        assertEquals(1, savedTasks.size(), "Only active non-disabled step task must be created");
        assertEquals(StepRole.SECURITY_MANAGER, savedTasks.get(0).getStepRole());
        assertEquals("Persetujuan Manager Security", savedTasks.get(0).getStepName());

        assertFalse(savedTasks.stream().anyMatch(t -> t.getStepRole() == StepRole.REQUESTOR_MANAGER),
                "Requestor Manager step must be completely omitted");
        assertFalse(savedTasks.stream().anyMatch(t -> t.getStepRole() == StepRole.PROCUREMENT_MANAGER),
                "Procurement Manager step must be completely omitted");
    }

    private void SesApprovalTaskAssigneeRepo(SesApprovalTaskRepository r) {}
}
