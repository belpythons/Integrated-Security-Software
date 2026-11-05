package com.badaklng.approval.model;

import com.badaklng.platform.model.BaseForm;
import com.badaklng.ses.validation.SafeText;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * Form for the approve/reject action on an approval task.
 */
@Getter
@Setter
@NoArgsConstructor
public class ApprovalActionForm extends BaseForm {

    @NotNull(message = "ID approval task wajib diisi")
    private Long instanceId;

    @NotBlank(message = "Aksi persetujuan wajib ditentukan")
    @Pattern(regexp = "^(?i)(approve|reject|revise)$", message = "Aksi harus berupa approve, reject, atau revise")
    private String action;   // "approve" | "reject"

    @Size(max = 500, message = "Catatan maksimal 500 karakter")
    @SafeText
    private String note;
}
