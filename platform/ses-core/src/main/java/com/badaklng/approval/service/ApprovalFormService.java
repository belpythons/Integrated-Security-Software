package com.badaklng.approval.service;

import com.badaklng.approval.entity.ChainResolutionTier;
import com.badaklng.approval.entity.SesApprovalChain;
import com.badaklng.approval.entity.SesForm;
import com.badaklng.approval.entity.SesFormApprovalMap;
import com.badaklng.approval.repository.SesApprovalChainRepository;
import com.badaklng.approval.repository.SesApprovalInstanceRepository;
import com.badaklng.approval.repository.SesApprovalStepRepository;
import com.badaklng.approval.repository.SesFormApprovalMapRepository;
import com.badaklng.approval.repository.SesFormRepository;
import com.badaklng.approval.spi.ApprovalFormDescriptor;
import com.badaklng.approval.spi.MatchKeyOption;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry (SES_FORM) + mapping (SES_FORM_APPROVAL_MAP) management, and the
 * runtime chain-resolution entry point used by {@link ApprovalService#trigger}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalFormService {

    private final SesFormRepository formRepository;
    private final SesFormApprovalMapRepository mapRepository;
    private final SesApprovalChainRepository chainRepository;
    private final SesApprovalStepRepository stepRepository;
    private final SesApprovalInstanceRepository instanceRepository;
    private final ObjectProvider<ApprovalFormDescriptor> descriptors;

    // ---------------------------------------------------------------
    // Registry sync
    // ---------------------------------------------------------------

    @Transactional
    public SesForm upsertFromDescriptor(ApprovalFormDescriptor d) {
        SesForm form = formRepository.findByFormCode(d.formCode()).orElseGet(SesForm::new);
        form.setFormCode(d.formCode());
        form.setFormName(d.formName());
        form.setMatchKeyLabel(d.matchKeyLabel());
        form.setActiveFlag("Y");
        form.setLastSeenDate(new Date());
        return formRepository.save(form);
    }

    @Transactional
    public int deactivateOrphans(Set<String> seenFormCodes) {
        int count = 0;
        for (SesForm form : formRepository.findAll()) {
            if (!seenFormCodes.contains(form.getFormCode()) && "Y".equals(form.getActiveFlag())) {
                form.setActiveFlag("N");
                formRepository.save(form);
                count++;
            }
        }
        return count;
    }

    /**
     * Guarded hard delete of an orphaned SES_FORM row: only proceeds when
     * neither mappings nor instances reference the form.
     */
    @Transactional
    public void hardDeleteOrphan(String formCode) {
        long mapCount = mapRepository.findByFormCodeOrderByMatchKeyAsc(formCode).size();
        long instanceCount = instanceRepository.countByFormCode(formCode);
        if (mapCount > 0 || instanceCount > 0) {
            throw new IllegalStateException("Cannot delete form " + formCode + ": "
                    + mapCount + " mapping(s), " + instanceCount + " instance(s) still reference it");
        }
        SesForm form = findByCode(formCode);
        formRepository.delete(form);
    }

    // ---------------------------------------------------------------
    // GUI reads
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SesForm> findAllForms() {
        return formRepository.findAllByOrderByActiveFlagDescFormNameAsc();
    }

    @Transactional(readOnly = true)
    public SesForm findByCode(String formCode) {
        return formRepository.findByFormCode(formCode)
                .orElseThrow(() -> new IllegalArgumentException("Form not found: " + formCode));
    }

    /**
     * Whether ANY mapping row (active or soft-deactivated) still references
     * this form — mirrors the raw check in {@link #hardDeleteOrphan}, so the
     * list screen's delete button reflects reality even though
     * {@link #findMappings} below only returns active rows.
     */
    @Transactional(readOnly = true)
    public boolean hasAnyMapping(String formCode) {
        return !mapRepository.findByFormCodeOrderByMatchKeyAsc(formCode).isEmpty();
    }

    /**
     * Active mapping rows only — this backs the list/edit screens, where a
     * removed override must actually disappear rather than reappear forever
     * as a disabled ghost row (saveMappings soft-deactivates rows instead of
     * deleting them so a re-added match key can reuse/reactivate its old row).
     */
    @Transactional(readOnly = true)
    public List<SesFormApprovalMap> findMappings(String formCode) {
        List<SesFormApprovalMap> all = mapRepository.findByFormCodeOrderByMatchKeyAsc(formCode);
        List<SesFormApprovalMap> active = new ArrayList<>();
        for (SesFormApprovalMap m : all) {
            if ("Y".equals(m.getActiveFlag())) {
                active.add(m);
            }
        }
        return active;
    }

    /**
     * Looks up the live descriptor bean for this formCode and returns its
     * matchKeys(). Returns an empty list (never throws) when no descriptor
     * currently matches — e.g. the form is an orphan.
     */
    @Transactional(readOnly = true)
    public List<MatchKeyOption> matchKeyOptions(String formCode) {
        return descriptors.orderedStream()
                .filter(d -> formCode.equals(d.formCode()))
                .findFirst()
                .map(ApprovalFormDescriptor::matchKeys)
                .orElse(Collections.emptyList());
    }

    /**
     * Looks up the live descriptor bean for this formCode. Empty when no
     * descriptor currently matches (e.g. the form is an orphan, or formCode
     * is null because the instance predates form-code tracking).
     */
    @Transactional(readOnly = true)
    public Optional<ApprovalFormDescriptor> findDescriptor(String formCode) {
        if (formCode == null) {
            return Optional.empty();
        }
        return descriptors.orderedStream()
                .filter(d -> formCode.equals(d.formCode()))
                .findFirst();
    }

    // ---------------------------------------------------------------
    // Mapping edit
    // ---------------------------------------------------------------

    /**
     * Replace-by-diff save of a form's mapping rows, in one transaction.
     *
     * NOTE: this intentionally does NOT take the controller layer's own
     * edit-form model (that model is owned by a concurrently-running agent
     * and its shape isn't visible here). The controller must map its form
     * into {@code defaultChainId} + a list of {@link OverrideInput} before
     * calling this method.
     */
    @Transactional
    public void saveMappings(String formCode, Long defaultChainId, List<OverrideInput> overrides) {
        // Fail fast on duplicate match keys among the incoming overrides,
        // before writing anything.
        if (overrides != null) {
            Set<String> seenKeys = new HashSet<>();
            for (OverrideInput o : overrides) {
                if (o == null || o.getMatchKey() == null || o.getMatchKey().trim().isEmpty()) {
                    continue;
                }
                String key = o.getMatchKey().trim();
                if (!seenKeys.add(key)) {
                    throw new IllegalArgumentException("Duplicate match key in mapping: " + key);
                }
            }
        }

        List<SesFormApprovalMap> existing = mapRepository.findByFormCodeOrderByMatchKeyAsc(formCode);
        SesFormApprovalMap existingDefault = existing.stream()
                .filter(m -> m.getMatchKey() == null)
                .findFirst()
                .orElse(null);
        Map<Long, SesFormApprovalMap> existingOverridesById = existing.stream()
                .filter(m -> m.getMatchKey() != null)
                .collect(Collectors.toMap(SesFormApprovalMap::getMapId, m -> m));

        try {
            // Default row (matchKey IS NULL).
            if (defaultChainId == null) {
                if (existingDefault != null && "Y".equals(existingDefault.getActiveFlag())) {
                    existingDefault.setActiveFlag("N");
                    mapRepository.save(existingDefault);
                }
            } else {
                SesFormApprovalMap def = existingDefault != null ? existingDefault : new SesFormApprovalMap();
                def.setFormCode(formCode);
                def.setMatchKey(null);
                def.setChainId(defaultChainId);
                def.setActiveFlag("Y");
                mapRepository.save(def);
            }

            // Overrides: find-or-create by mapId, deactivate DB rows absent
            // from the incoming list.
            Set<Long> touchedIds = new HashSet<>();
            if (overrides != null) {
                for (OverrideInput o : overrides) {
                    if (o == null || o.getMatchKey() == null || o.getMatchKey().trim().isEmpty()
                            || o.getChainId() == null) {
                        continue;
                    }
                    SesFormApprovalMap row;
                    if (o.getMapId() != null) {
                        row = existingOverridesById.get(o.getMapId());
                        if (row == null) {
                            row = mapRepository.findById(o.getMapId())
                                    .orElseThrow(() -> new IllegalArgumentException("Mapping row not found: " + o.getMapId()));
                        }
                    } else {
                        row = new SesFormApprovalMap();
                        row.setFormCode(formCode);
                    }
                    row.setMatchKey(o.getMatchKey().trim());
                    row.setChainId(o.getChainId());
                    row.setActiveFlag(o.getActiveFlag() != null ? o.getActiveFlag() : "Y");
                    row = mapRepository.save(row);
                    touchedIds.add(row.getMapId());
                }
            }

            for (SesFormApprovalMap row : existingOverridesById.values()) {
                if (!touchedIds.contains(row.getMapId()) && "Y".equals(row.getActiveFlag())) {
                    row.setActiveFlag("N");
                    mapRepository.save(row);
                }
            }

            // Force the unique index check to run now, inside this
            // try/catch, rather than surfacing an opaque ORA-00001 at
            // transaction commit outside this method.
            mapRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException(
                    "Could not save mapping — a mapping for this form and match key already exists.", ex);
        }
    }

    /**
     * One row of the mapping override list. A small nested DTO instead of
     * the controller layer's own form-model class, so this class compiles
     * independently of the concurrently-written controller/model layer;
     * the controller adapts by mapping its own form into these.
     */
    @Getter
    @Setter
    public static class OverrideInput {
        private Long mapId;
        private String matchKey;
        private Long chainId;
        private String activeFlag = "Y";
    }

    // ---------------------------------------------------------------
    // Runtime resolution
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public ChainResolution resolveChain(String formCode, String matchKey) {
        SesForm form = formRepository.findByFormCode(formCode)
                .filter(f -> "Y".equals(f.getActiveFlag()))
                .orElseThrow(() -> new ApprovalConfigurationException("Approval form not registered or inactive: " + formCode));

        if (matchKey != null) {
            Optional<SesFormApprovalMap> override = mapRepository.findByFormCodeAndMatchKeyAndActiveFlag(formCode, matchKey, "Y");
            if (override.isPresent()) {
                Optional<SesApprovalChain> chain = validChain(override.get().getChainId());
                if (chain.isPresent()) {
                    return new ChainResolution(chain.get(), ChainResolutionTier.OVERRIDE, matchKey);
                }
                log.warn("Override mapping {} -> chain {} is broken (inactive/missing/no steps); falling back to default",
                        matchKey, override.get().getChainId());
            }
        }

        SesFormApprovalMap def = mapRepository.findActiveDefault(formCode)
                .orElseThrow(() -> new ApprovalConfigurationException("No default approval chain configured for form: " + formCode));
        SesApprovalChain chain = validChain(def.getChainId())
                .orElseThrow(() -> new ApprovalConfigurationException("Default chain for form " + formCode + " is inactive, missing, or has no steps"));
        return new ChainResolution(chain, ChainResolutionTier.DEFAULT, null);
    }

    private Optional<SesApprovalChain> validChain(Long chainId) {
        return chainRepository.findById(chainId)
                .filter(c -> "Y".equals(c.getActiveFlag()))
                .filter(c -> stepRepository.countByChainId(c.getChainId()) > 0);
    }

    @Getter
    @AllArgsConstructor
    public static class ChainResolution {
        private final SesApprovalChain chain;
        private final ChainResolutionTier tier;
        private final String matchKeyUsed;
    }
}
