package com.badaklng.approval.controller;

import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.platform.system.service.AppLogService;
import com.badaklng.approval.entity.SesApprovalChain;
import com.badaklng.approval.entity.SesForm;
import com.badaklng.approval.entity.SesFormApprovalMap;
import com.badaklng.approval.model.SesFormMappingForm;
import com.badaklng.approval.service.ApprovalChainService;
import com.badaklng.approval.service.ApprovalFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Form-to-chain mapping admin screen under /approval/map (read) +
 * /approval/map/form (virtual CRUD-grant URL). SES_FORM rows themselves
 * are descriptor-synced and read-only (see ApprovalFormRegistrySync);
 * only the default-chain and override mapping rows are editable here.
 *
 * Deliberately mapped at /edit/{formCode}, NOT a bare /{formCode}: with
 * the virtual page URL /approval/map/form registered in APP_PAGE, a bare
 * /{formCode} GET mapping would ambiguously collide with the literal
 * "form" path segment at boot — a previously recorded incident.
 */
@Controller
@RequestMapping("/approval/map")
@RequiredArgsConstructor
public class ApprovalFormMapController {

    private final ApprovalFormService formService;
    private final ApprovalChainService chainService;
    private final PageAccessGuard pageAccessGuard;
    private final AppLogService logService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(SesFormMappingForm.LIST_URL));
        List<SesForm> forms = formService.findAllForms();

        Map<Long, String> chainNames = new HashMap<>();
        for (SesApprovalChain c : chainService.findAll()) {
            chainNames.put(c.getChainId(), c.getChainName());
        }

        Map<String, String> defaultChainNameByFormCode = new LinkedHashMap<>();
        Map<String, Integer> overrideCountByFormCode = new LinkedHashMap<>();
        Map<String, Boolean> deletableByFormCode = new LinkedHashMap<>();
        for (SesForm f : forms) {
            List<SesFormApprovalMap> mappings = formService.findMappings(f.getFormCode());
            String defaultChainName = null;
            int overrideCount = 0;
            for (SesFormApprovalMap m : mappings) {
                if (m.getMatchKey() == null) {
                    defaultChainName = chainNames.get(m.getChainId());
                } else {
                    overrideCount++;
                }
            }
            defaultChainNameByFormCode.put(f.getFormCode(), defaultChainName);
            overrideCountByFormCode.put(f.getFormCode(), overrideCount);
            // Uses the raw (active + inactive) reference count, not the filtered
            // `mappings` above — a form with only soft-deactivated rows must still
            // show as non-deletable, matching what hardDeleteOrphan actually enforces.
            deletableByFormCode.put(f.getFormCode(), !formService.hasAnyMapping(f.getFormCode()));
        }

        model.addAttribute("forms", forms);
        model.addAttribute("defaultChainNameByFormCode", defaultChainNameByFormCode);
        model.addAttribute("overrideCountByFormCode", overrideCountByFormCode);
        model.addAttribute("deletableByFormCode", deletableByFormCode);
        return "approval/map/list";
    }

    @GetMapping("/edit/{formCode}")
    public String edit(@PathVariable String formCode, Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(SesFormMappingForm.FORM_URL));
        SesForm form = formService.findByCode(formCode);
        List<SesFormApprovalMap> mappings = formService.findMappings(formCode);
        model.addAttribute("form", SesFormMappingForm.from(form, mappings));
        model.addAttribute("chains", chainsForDropdown(mappings));
        model.addAttribute("matchKeyOptions", formService.matchKeyOptions(formCode));
        return "approval/map/edit";
    }

    /**
     * Selectable chains, plus any chain this form is already wired to even if
     * it has since gone inactive/stepless — otherwise that chain's
     * {@code <option>} silently disappears from the dropdown and re-saving
     * the form (e.g. to edit an unrelated field) nulls the mapping out from
     * under the admin. Mirrors the "unregistered checkpoint key" pattern in
     * chain/edit.html.
     */
    private List<SesApprovalChain> chainsForDropdown(List<SesFormApprovalMap> mappings) {
        List<SesApprovalChain> chains = new ArrayList<>(chainService.findSelectable());
        Set<Long> seen = new HashSet<>();
        for (SesApprovalChain c : chains) {
            seen.add(c.getChainId());
        }
        for (SesFormApprovalMap m : mappings) {
            if (m.getChainId() != null && seen.add(m.getChainId())) {
                chains.add(chainService.findById(m.getChainId()));
            }
        }
        return chains;
    }

    @PostMapping("/save")
    public String save(@ModelAttribute SesFormMappingForm form, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(SesFormMappingForm.FORM_URL, "SUBMIT");
        List<ApprovalFormService.OverrideInput> overrides = new ArrayList<>();
        if (form.getOverrides() != null) {
            for (SesFormMappingForm.OverrideRow row : form.getOverrides()) {
                if (row == null || row.getMatchKey() == null || row.getMatchKey().trim().isEmpty()) {
                    continue;
                }
                ApprovalFormService.OverrideInput input = new ApprovalFormService.OverrideInput();
                input.setMapId(row.getMapId());
                input.setMatchKey(row.getMatchKey().trim());
                input.setChainId(row.getChainId());
                input.setActiveFlag("Y".equalsIgnoreCase(row.getActiveFlag()) ? "Y" : "N");
                overrides.add(input);
            }
        }
        try {
            formService.saveMappings(form.getFormCode(), form.getDefaultChainId(), overrides);
            logService.log("UPDATE", "ApprovalFormMap", "Saved mappings for form " + form.getFormCode(),
                    null, "SES_FORM_APPROVAL_MAP", request);
            ra.addFlashAttribute("successMsg", "Mapping saved.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
            return "redirect:" + SesFormMappingForm.LIST_URL + "/edit/" + form.getFormCode();
        }
        return "redirect:" + SesFormMappingForm.LIST_URL;
    }

    /**
     * Deletes an orphaned SES_FORM row (descriptor bean removed). The
     * delete button is disabled client-side unless the form currently has
     * zero mapping rows (see list()'s deletableByFormCode); the service is
     * still the authority (ApprovalFormService.hardDeleteOrphan also
     * refuses when instances still reference the form) and any refusal is
     * surfaced here as a flash error.
     */
    @PostMapping("/{formCode}/delete")
    public String deleteOrphan(@PathVariable String formCode, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(SesFormMappingForm.FORM_URL, "DELETE");
        try {
            formService.hardDeleteOrphan(formCode);
            logService.log("DELETE", "ApprovalFormMap", "Deleted orphaned form " + formCode,
                    null, "SES_FORM", request);
            ra.addFlashAttribute("successMsg", "Form entry deleted.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:" + SesFormMappingForm.LIST_URL;
    }
}
