package com.badaklng.sticker.controller;

import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.platform.system.service.AppLogService;
import com.badaklng.sticker.entity.SesStickerType;
import com.badaklng.sticker.model.StickerTypeForm;
import com.badaklng.sticker.service.StickerTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Jenis Sticker" master admin screen, /admin/sticker-type (read) +
 * /admin/sticker-type/form (virtual CRUD-grant URL). Simpler sibling of
 * RequestTypeController — no document-requirement sub-table, just a
 * code/name/price/order/active master.
 */
@Controller
@RequestMapping("/admin/sticker-type")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSADM')")
@RequiredArgsConstructor
public class StickerTypeController {

    private final StickerTypeService typeService;
    private final PageAccessGuard pageAccessGuard;
    private final AppLogService logService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(StickerTypeForm.LIST_URL));
        List<SesStickerType> types = typeService.findAll();
        model.addAttribute("types", types);
        Map<String, Boolean> inUse = new HashMap<>();
        for (SesStickerType type : types) {
            inUse.put(type.getStickerTypeCode(), typeService.isInUse(type.getStickerTypeCode()));
        }
        model.addAttribute("typeInUse", inUse);
        return "admin/sticker-type/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(StickerTypeForm.FORM_URL));
        model.addAttribute("typeForm", new StickerTypeForm());
        return "admin/sticker-type/form";
    }

    @GetMapping("/{code}")
    public String edit(@PathVariable String code, Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(StickerTypeForm.FORM_URL));
        model.addAttribute("typeForm", StickerTypeForm.from(typeService.findByCode(code)));
        return "admin/sticker-type/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute StickerTypeForm form, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(StickerTypeForm.FORM_URL, "SUBMIT");
        boolean isCreate = !form.isEdit();
        try {
            SesStickerType saved = typeService.save(form.toEntity(), isCreate);
            logService.log(isCreate ? "SAVE" : "UPDATE", "SesStickerType",
                    "Saved Jenis Sticker " + saved.getStickerTypeCode(), null, "SES_STICKER_TYPE", request);
            ra.addFlashAttribute("successMsg", "Jenis Sticker berhasil disimpan.");
            return "redirect:" + StickerTypeForm.LIST_URL;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
            return "redirect:" + StickerTypeForm.LIST_URL + (isCreate ? "/new" : "/" + form.getStickerTypeCode());
        }
    }

    @PostMapping("/{code}/delete")
    public String delete(@PathVariable String code, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(StickerTypeForm.FORM_URL, "DELETE");
        try {
            typeService.delete(code);
            logService.log("DELETE", "SesStickerType", "Deleted Jenis Sticker " + code, null, "SES_STICKER_TYPE", request);
            ra.addFlashAttribute("successMsg", "Jenis Sticker dihapus.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:" + StickerTypeForm.LIST_URL;
    }
}
