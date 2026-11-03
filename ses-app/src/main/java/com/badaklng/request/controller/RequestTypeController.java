package com.badaklng.request.controller;

import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.platform.system.service.AppLogService;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.model.RequestTypeForm;
import com.badaklng.request.service.RequestTypeService;
import com.badaklng.approval.service.ApprovalChainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request Type Master admin screen, /admin/request-type (read) +
 * /admin/request-type/form (virtual CRUD-grant URL). This screen is what
 * makes new request types and new document requirements a no-redeploy
 * operation (RequestApprovalBridge.matchKeys() reads SES_REQUEST_TYPE live).
 */
@Controller
@RequestMapping("/admin/request-type")
@RequiredArgsConstructor
public class RequestTypeController {

    private final RequestTypeService requestTypeService;
    private final PageAccessGuard pageAccessGuard;
    private final AppLogService logService;
    private final ApprovalChainService approvalChainService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(RequestTypeForm.LIST_URL));
        List<SesRequestType> types = requestTypeService.findAll();
        model.addAttribute("types", types);
        Map<String, Boolean> inUse = new HashMap<>();
        for (SesRequestType type : types) {
            inUse.put(type.getTypeCode(), requestTypeService.isInUse(type.getTypeCode()));
        }
        model.addAttribute("typeInUse", inUse);
        return "admin/request-type/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(RequestTypeForm.FORM_URL));
        model.addAttribute("typeForm", RequestTypeForm.blank(requestTypeService.buildDocSelectionsFor(null)));
        model.addAttribute("allDocs", requestTypeService.catalogFor(null));
        model.addAttribute("approvalChains", approvalChainService.findSelectable());
        return "admin/request-type/form";
    }

    @GetMapping("/{typeCode}")
    public String edit(@PathVariable String typeCode, Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(RequestTypeForm.FORM_URL));
        SesRequestType type = requestTypeService.findByCode(typeCode);
        List<RequestTypeService.DocSelection> selections = requestTypeService.buildDocSelectionsFor(typeCode);
        Long mappedChainId = requestTypeService.getMappedChainId(typeCode);
        model.addAttribute("typeForm", RequestTypeForm.from(type, selections, mappedChainId));
        model.addAttribute("allDocs", requestTypeService.catalogFor(typeCode));
        model.addAttribute("approvalChains", approvalChainService.findSelectable());
        return "admin/request-type/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute RequestTypeForm form, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(RequestTypeForm.FORM_URL, "SUBMIT");
        boolean isCreate = !form.isEdit();
        try {
            SesRequestType saved = requestTypeService.saveWithDocs(form.toEntity(), form.toDocSelections(), form.getChainId(), isCreate);
            logService.log(isCreate ? "SAVE" : "UPDATE", "SesRequestType",
                    "Saved request type " + saved.getTypeCode(), null, "SES_REQUEST_TYPE", request);
            ra.addFlashAttribute("successMsg", "Request type saved.");
            return "redirect:" + RequestTypeForm.LIST_URL;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
            return "redirect:" + RequestTypeForm.LIST_URL + (isCreate ? "/new" : "/" + form.getTypeCode());
        }
    }

    @PostMapping("/{typeCode}/delete")
    public String delete(@PathVariable String typeCode, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(RequestTypeForm.FORM_URL, "DELETE");
        try {
            requestTypeService.delete(typeCode);
            logService.log("DELETE", "SesRequestType", "Deleted request type " + typeCode, null, "SES_REQUEST_TYPE", request);
            ra.addFlashAttribute("successMsg", "Request type deleted.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:" + RequestTypeForm.LIST_URL;
    }

    @PostMapping("/doc/{docCode}/delete")
    public String deleteDoc(@PathVariable String docCode,
                             @RequestParam(required = false) String returnTypeCode,
                             RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(RequestTypeForm.FORM_URL, "DELETE");
        try {
            boolean deactivated = requestTypeService.deleteDocRequirement(docCode);
            logService.log("DELETE", "SesDocRequirement",
                    (deactivated ? "Deactivated" : "Deleted") + " doc requirement " + docCode, null, "SES_DOC_REQUIREMENT", request);
            ra.addFlashAttribute("successMsg", deactivated
                    ? "Document still in use elsewhere — deactivated instead of deleted."
                    : "Document deleted.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:" + RequestTypeForm.LIST_URL + (returnTypeCode == null || returnTypeCode.trim().isEmpty()
                ? "/new" : "/" + returnTypeCode);
    }

    @PostMapping("/doc/{docCode}/reactivate")
    public String reactivateDoc(@PathVariable String docCode,
                                 @RequestParam(required = false) String returnTypeCode,
                                 RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(RequestTypeForm.FORM_URL, "UPDATE");
        try {
            requestTypeService.reactivateDocRequirement(docCode);
            logService.log("UPDATE", "SesDocRequirement", "Reactivated doc requirement " + docCode, null, "SES_DOC_REQUIREMENT", request);
            ra.addFlashAttribute("successMsg", "Document reactivated.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:" + RequestTypeForm.LIST_URL + (returnTypeCode == null || returnTypeCode.trim().isEmpty()
                ? "/new" : "/" + returnTypeCode);
    }
}
