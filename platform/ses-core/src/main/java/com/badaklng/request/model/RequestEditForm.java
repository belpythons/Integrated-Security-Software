package com.badaklng.request.model;

import com.badaklng.platform.model.BaseForm;
import com.badaklng.request.entity.ScreeningStatus;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestEvaluator;
import com.badaklng.request.entity.SesRequestWorker;
import com.badaklng.request.entity.SesRequestWorkerDoc;
import com.badaklng.request.service.RequestSaveInput;
import com.badaklng.request.service.RequestService;
import com.badaklng.ses.validation.SafeText;
import com.badaklng.ses.validation.ValidFileExtension;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Web binding target for the request form, posted with Spring indexed
 * binding: {@code workers[i].docs[j].*} — mirrors ApprovalChainEditForm's
 * nested-Row-with-public-no-arg-constructor pattern exactly.
 * <p>
 * Date fields use {@code yyyy-MM-dd} — pair with an HTML {@code type="date"}
 * input or a flatpickr instance configured with {@code dateFormat: "Y-m-d"}.
 */
@Getter
@Setter
@NoArgsConstructor
public class RequestEditForm extends BaseForm {

    public static final String LIST_URL = "/request";
    public static final String FORM_URL = "/request/form";

    /** Null on create. */
    private Long requestId;

    @NotBlank(message = "Jenis Pengajuan wajib dipilih")
    @Size(max = 30, message = "Kode jenis pengajuan maksimal 30 karakter")
    private String typeCode;

    @Size(max = 100, message = "Nomor kontrak maksimal 100 karakter")
    @SafeText
    private String contractNo;

    @Size(max = 50, message = "Tipe kontrak maksimal 50 karakter")
    @SafeText
    private String contractType;

    @Size(max = 255, message = "Judul kontrak maksimal 255 karakter")
    @SafeText
    private String contractTitle;

    @Size(max = 30, message = "Kode zona maksimal 30 karakter")
    private String zoneCode;

    @Size(max = 100, message = "Instansi maksimal 100 karakter")
    @SafeText
    private String instance;

    @Size(max = 255, message = "Alamat maksimal 255 karakter")
    @SafeText
    private String address;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startPeriod;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date finishPeriod;

    /** Proof of Payment — new upload. */
    @ValidFileExtension(message = "Bukti pembayaran harus berformat PDF, JPG, atau PNG")
    private MultipartFile paymentFile;

    /** Carries forward an already-uploaded Proof of Payment across an edit. */
    private String existingPaymentFileId;

    /**
     * Where the document-upload link is sent.
     *
     * <p><b>The contractor's address, not the requestor's.</b> The worker
     * documents are held outside Badak LNG, and this is how they get in —
     * which is also why the page that link opens needs no login. Mandatory
     * for a submit; Save as Draft never validates it.</p>
     */
    @Email(message = "Format email kontraktor tidak valid")
    @Size(max = 100, message = "Email maksimal 100 karakter")
    private String email;

    @Valid
    private List<WorkerRow> workers = new ArrayList<>();

    /**
     * Staf Security picked as step-1 evaluators, as {@code empl_id}s — bound
     * from a select2 multi-select, so Spring delivers one list entry per
     * selected option and an empty list when none are chosen.
     *
     * <p>Only ids, never names: see {@link RequestSaveInput#getEvaluatorEmplIds()}.</p>
     */
    private List<String> evaluatorEmplIds = new ArrayList<>();

    /**
     * Populates an edit form from an existing request, its workers (already
     * in line_no order), a worker-id -> doc rows lookup, and the type's
     * currently-required documents (in order, with display name + mandatory
     * flag) — so every worker row shows one document cell per requirement,
     * labelled and pre-filled with the existing fileId where already
     * uploaded. The docName/mandatory carried on each WorkerDocRow lets the
     * template render a human-readable label on the server-side initial
     * render without a round trip (the client-side type-change rebuild uses
     * the same shape via the /request/requirements/{typeCode} JSON endpoint).
     */
    public static RequestEditForm from(SesRequest request, List<SesRequestWorker> workers,
            Map<Long, List<SesRequestWorkerDoc>> docsByWorkerId, List<RequestService.RequirementRow> requirements,
            String currentPaymentFileId, List<SesRequestEvaluator> evaluators) {
        RequestEditForm f = new RequestEditForm();
        if (evaluators != null) {
            List<String> ids = new ArrayList<>();
            for (SesRequestEvaluator e : evaluators) {
                ids.add(e.getEmplId());
            }
            f.setEvaluatorEmplIds(ids);
        }
        f.setRequestId(request.getRequestId());
        f.setTypeCode(request.getTypeCode());
        f.setContractNo(request.getContractNo());
        f.setContractType(request.getContractType());
        f.setContractTitle(request.getContractTitle());
        f.setZoneCode(request.getZoneCode());
        f.setInstance(request.getInstance());
        f.setAddress(request.getAddress());
        f.setEmail(request.getEmail());
        f.setStartPeriod(request.getStartPeriod());
        f.setFinishPeriod(request.getFinishPeriod());
        // Sourced from PaymentService (via the caller), NOT request.getPaymentFileId()
        // — that column is dead once com.badaklng.payment exists (see
        // RequestService.saveAndSubmit step 4 / RequestService.detailModel).
        f.setExistingPaymentFileId(currentPaymentFileId);

        List<WorkerRow> rows = new ArrayList<>();
        if (workers != null) {
            for (SesRequestWorker w : workers) {
                WorkerRow row = new WorkerRow();
                row.setWorkerId(w.getWorkerId());
                row.setWorkerName(w.getWorkerName());
                row.setNik(w.getNik());
                row.setIdType(w.getIdType());
                row.setBadge(w.getBadge());
                row.setJabatan(w.getJabatan());
                row.setDepartment(w.getDepartment());
                row.setSection(w.getSection());
                row.setGender(w.getGender());
                row.setScreeningStatus(w.getScreeningStatus());
                row.setScreeningStatusLabel(ScreeningStatus.label(w.getScreeningStatus()));
                row.setScreeningDate(w.getScreeningDate());
                row.setScreeningReason(w.getScreeningReason());

                List<SesRequestWorkerDoc> existingDocs = docsByWorkerId != null
                        ? docsByWorkerId.get(w.getWorkerId())
                        : null;
                List<WorkerDocRow> docRows = new ArrayList<>();
                if (requirements != null) {
                    for (RequestService.RequirementRow req : requirements) {
                        WorkerDocRow docRow = new WorkerDocRow();
                        docRow.setDocCode(req.getDocCode());
                        docRow.setDocName(req.getDocName());
                        docRow.setMandatory(req.isMandatory());
                        if (existingDocs != null) {
                            existingDocs.stream()
                                    .filter(d -> d.getDocCode().equals(req.getDocCode()))
                                    .findFirst()
                                    .ifPresent(d -> docRow.setExistingFileId(d.getFileId()));
                        }
                        docRows.add(docRow);
                    }
                }
                row.setDocs(docRows);
                rows.add(row);
            }
        }
        f.setWorkers(rows);
        return f;
    }

    public RequestSaveInput toServiceInput() {
        RequestSaveInput input = new RequestSaveInput();
        input.setRequestId(requestId);
        input.setTypeCode(typeCode);
        input.setContractNo(contractNo);
        input.setContractType(contractType);
        input.setContractTitle(contractTitle);
        input.setZoneCode(zoneCode);
        input.setInstance(instance);
        input.setAddress(address);
        input.setEmail(email);
        input.setStartPeriod(startPeriod);
        input.setFinishPeriod(finishPeriod);
        input.setPaymentFile(paymentFile);
        input.setExistingPaymentFileId(existingPaymentFileId);
        input.setEvaluatorEmplIds(evaluatorEmplIds != null ? evaluatorEmplIds : new ArrayList<String>());

        List<RequestSaveInput.WorkerInput> workerInputs = new ArrayList<>();
        if (workers != null) {
            for (WorkerRow row : workers) {
                if (row == null) {
                    continue;
                }
                RequestSaveInput.WorkerInput wi = new RequestSaveInput.WorkerInput();
                wi.setWorkerId(row.getWorkerId());
                wi.setWorkerName(row.getWorkerName());
                wi.setNik(row.getNik());
                wi.setIdType(row.getIdType());
                wi.setBadge(row.getBadge());
                wi.setJabatan(row.getJabatan());
                wi.setDepartment(row.getDepartment());
                wi.setSection(row.getSection());
                wi.setGender(row.getGender());

                List<RequestSaveInput.WorkerDocInput> docInputs = new ArrayList<>();
                if (row.getDocs() != null) {
                    for (WorkerDocRow docRow : row.getDocs()) {
                        if (docRow == null || docRow.getDocCode() == null) {
                            continue;
                        }
                        RequestSaveInput.WorkerDocInput di = new RequestSaveInput.WorkerDocInput();
                        di.setDocCode(docRow.getDocCode());
                        di.setFile(docRow.getFile());
                        di.setExistingFileId(docRow.getExistingFileId());
                        docInputs.add(di);
                    }
                }
                wi.setDocs(docInputs);
                workerInputs.add(wi);
            }
        }
        input.setWorkers(workerInputs);
        return input;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class WorkerRow {
        private Long workerId;

        @NotBlank(message = "Nama pekerja wajib diisi")
        @Size(max = 100, message = "Nama pekerja maksimal 100 karakter")
        @SafeText
        private String workerName;

        @NotBlank(message = "Nomor identitas (NIK / Passport) wajib diisi")
        @Size(min = 3, max = 30, message = "Nomor identitas harus antara 3 hingga 30 karakter")
        private String nik;

        private String idType = "NIK";

        @Pattern(regexp = "^$|^[A-Za-z0-9_-]+$", message = "Format badge hanya boleh huruf, angka, dan strip")
        @Size(max = 30, message = "Nomor badge maksimal 30 karakter")
        private String badge;

        @Size(max = 100, message = "Jabatan maksimal 100 karakter")
        @SafeText
        private String jabatan;

        @Size(max = 100, message = "Departemen maksimal 100 karakter")
        @SafeText
        private String department;

        @Size(max = 100, message = "Seksi maksimal 100 karakter")
        @SafeText
        private String section;

        @NotBlank(message = "Jenis kelamin wajib dipilih")
        private String gender;
        private String screeningStatus;
        private String screeningStatusLabel;
        private java.util.Date screeningDate;

        @Size(max = 500, message = "Catatan screening maksimal 500 karakter")
        @SafeText
        private String screeningReason;

        @Valid
        private List<WorkerDocRow> docs = new ArrayList<>();

        /**
         * Maps this one row to the service-layer worker input.
         *
         * <p>On {@code WorkerRow} rather than inline in
         * {@link RequestEditForm#toServiceInput()} because the worker modal
         * binds a SINGLE row — {@code POST /request/{id}/worker/save} posts one
         * worker, not the whole form — and both doors must produce exactly the
         * same shape. Two copies of this mapping would drift.</p>
         */
        public RequestSaveInput.WorkerInput toWorkerInput() {
            RequestSaveInput.WorkerInput wi = new RequestSaveInput.WorkerInput();
            wi.setWorkerId(workerId);
            wi.setWorkerName(workerName);
            wi.setNik(nik);
            wi.setIdType(idType);
            wi.setBadge(badge);
            wi.setJabatan(jabatan);
            wi.setDepartment(department);
            wi.setSection(section);
            wi.setGender(gender);

            List<RequestSaveInput.WorkerDocInput> docInputs = new ArrayList<>();
            if (docs != null) {
                for (WorkerDocRow dr : docs) {
                    if (dr == null || dr.getDocCode() == null) {
                        continue;
                    }
                    RequestSaveInput.WorkerDocInput di = new RequestSaveInput.WorkerDocInput();
                    di.setDocCode(dr.getDocCode());
                    di.setFile(dr.getFile());
                    di.setExistingFileId(dr.getExistingFileId());
                    docInputs.add(di);
                }
            }
            wi.setDocs(docInputs);
            return wi;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class WorkerDocRow {
        private String docCode;
        /** Display-only, for the initial server-rendered label — not posted back meaningfully. */
        private String docName;
        /** Display-only, for the initial server-rendered Required/Optional badge. */
        private boolean mandatory;

        @ValidFileExtension(message = "Berkas lampiran harus berformat PDF, JPG, atau PNG")
        private MultipartFile file;

        private String existingFileId;
    }
}
