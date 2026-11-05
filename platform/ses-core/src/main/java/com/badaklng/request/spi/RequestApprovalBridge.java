package com.badaklng.request.spi;

import com.badaklng.approval.entity.ApprovalStatus;
import com.badaklng.approval.spi.ApprovalFormDescriptor;
import com.badaklng.approval.spi.ApprovalOutcomeListener;
import com.badaklng.approval.spi.MatchKeyOption;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.model.RequestEditForm;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.request.service.RequestService;
import com.badaklng.ses.jdbc.InChunks;
import com.badaklng.ses.text.SesText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The ENTIRE coupling surface between com.badaklng.request and the approval
 * module. This is the ONLY class in the request package allowed to import
 * com.badaklng.approval.spi.* or com.badaklng.approval.entity.ApprovalStatus
 * — everything else in this module calls ApprovalService (a plain @Service
 * bean) but never touches approval's entity/spi types directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestApprovalBridge implements ApprovalFormDescriptor, ApprovalOutcomeListener {

    private final SesRequestTypeRepository requestTypeRepository;
    private final SesRequestRepository requestRepository;
    private final RequestService requestService;
    private final SesText text;

    @Override
    public String formCode() {
        return "SES_REQUEST";
    }

    @Override
    public String formName() {
        return text.get("SES_TXT_MODULE_REQUEST_NAME", "Pengajuan Badge Karyawan");
    }

    @Override
    public String matchKeyLabel() {
        return text.get("SES_TXT_MODULE_REQUEST_MATCHKEY", "Jenis Badge");
    }

    @Override
    public List<MatchKeyOption> matchKeys() {
        return requestTypeRepository.findByActiveFlagOrderByOrderByAsc("Y").stream()
                .map(t -> new MatchKeyOption(t.getTypeCode(), t.getTypeName()))
                .collect(Collectors.toList());
    }

    @Override
    public String businessRefType() {
        return "SES_REQUEST";
    }

    @Override
    public String transactionId(Long businessRefId) {
        return requestRepository.findById(businessRefId).map(SesRequest::getRequestNo).orElse(null);
    }

    @Override
    public String description(Long businessRefId) {
        return requestRepository.findById(businessRefId)
                .map(r -> requestTypeRepository.findById(r.getTypeCode()).map(t -> t.getTypeName()).orElse(r.getTypeCode()))
                .orElse(null);
    }

    /**
     * Two queries for a whole worklist page: the requests, then their types.
     * Falls back to the raw type code when the type row is gone, exactly as
     * the single-row {@link #description} does.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, FormRowData> rowData(Collection<Long> businessRefIds) {
        Map<Long, FormRowData> out = new LinkedHashMap<>();
        if (businessRefIds == null || businessRefIds.isEmpty()) {
            return out;
        }
        List<SesRequest> requests = InChunks.flatMap(businessRefIds, requestRepository::findAllById);
        Map<String, String> typeNameByCode = new HashMap<>();
        List<String> codes = requests.stream()
                .map(SesRequest::getTypeCode)
                .filter(code -> code != null)
                .distinct()
                .collect(Collectors.toList());
        for (SesRequestType t : InChunks.flatMap(codes, requestTypeRepository::findAllById)) {
            typeNameByCode.put(t.getTypeCode(), t.getTypeName());
        }
        for (SesRequest r : requests) {
            out.put(r.getRequestId(), new FormRowData(r.getRequestNo(),
                    typeNameByCode.getOrDefault(r.getTypeCode(), r.getTypeCode())));
        }
        return out;
    }

    /**
     * Same read-only detail data RequestController#view builds for the
     * request owner (contract/worker/attachment fields), reused here so an
     * approver reviewing the instance sees the same thing — see
     * request/fragments/request-detail.html. Delegates to
     * RequestService#detailModel, the single source of this data (also used
     * by the payment module's detail pages for "which request is this for").
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> detailModel(Long businessRefId) {
        return requestService.detailModel(businessRefId);
    }

    /**
     * The same "Request Details" rows the requestor and the approver both see
     * on screen (request/fragments/request-detail.html), flattened for the
     * notification email. Labels and order are kept identical to that fragment
     * on purpose — an approver should not have to translate between the mail
     * and the page it links to.
     *
     * <p>Reads {@link RequestService#detailModel} rather than the repository,
     * so the mail cannot drift from the page: both derive from that one
     * method. Blank fields are dropped, not dashed — see
     * {@link ApprovalFormDescriptor#emailDetail}. "Proof of Payment" is a file
     * and has no text form; the button in the mail is how you get to it.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, String> emailDetail(Long businessRefId) {
        Map<String, Object> model = requestService.detailModel(businessRefId);
        RequestEditForm form = (RequestEditForm) model.get("requestForm");
        if (form == null) {
            return Collections.emptyMap();
        }
        SimpleDateFormat date = new SimpleDateFormat("dd-MM-yyyy");

        Map<String, String> rows = new LinkedHashMap<>();
        put(rows, "Type of Request", (String) model.get("typeName"));
        put(rows, "Contract Number", form.getContractNo());
        put(rows, "Contract Type", form.getContractType());
        put(rows, "Contract Title", form.getContractTitle());
        put(rows, "Zone Access", (String) model.get("zoneName"));
        put(rows, "Instansi", form.getInstance());
        put(rows, "Address", form.getAddress());
        put(rows, "Start Period", format(date, form.getStartPeriod()));
        put(rows, "Finish Period", format(date, form.getFinishPeriod()));
        put(rows, "Payment Amount", rupiah((Long) model.get("paymentAmount")));
        // One row instead of one per worker: the worker count is unbounded and
        // the mail body column is not (4000 chars, truncated silently).
        if (!form.getWorkers().isEmpty()) {
            rows.put("Jumlah Pekerja", String.valueOf(form.getWorkers().size()));
        }
        return rows;
    }

    private static void put(Map<String, String> rows, String label, String value) {
        if (value != null && !value.trim().isEmpty()) {
            rows.put(label, value.trim());
        }
    }

    private static String format(SimpleDateFormat f, Date d) {
        return d == null ? null : f.format(d);
    }

    /** Mirrors the page's {@code #numbers.formatInteger(x, 1, 'POINT')}. */
    private static String rupiah(Long amount) {
        return amount == null ? null : "Rp " + String.format("%,d", amount).replace(',', '.');
    }

    /** {@code RequestController#view} — the requestor's own read-only page. */
    @Override
    public String requestorViewPath(Long businessRefId) {
        return "/request/" + businessRefId + "/view";
    }

    /** SES_REQUEST.EMAIL — the "Email Penerima Dokumen" captured at submission. */
    @Override
    public String extNotifyEmail(Long businessRefId) {
        return requestRepository.findById(businessRefId).map(SesRequest::getEmail).orElse(null);
    }

    /**
     * Sets SES_REQUEST's OWN status field (plain String, not the approval
     * enum) to match the approval outcome. If the request row is somehow
     * gone, log a warning rather than throw — the approval transaction must
     * not fail just because a business row was deleted out from under it
     * (should never happen in practice; nothing in this module deletes
     * SES_REQUEST rows).
     */
    @Override
    @Transactional
    public void onCompleted(Long businessRefId, ApprovalStatus finalStatus) {
        Optional<SesRequest> found = requestRepository.findById(businessRefId);
        if (!found.isPresent()) {
            log.warn("ApprovalOutcomeListener: no SES_REQUEST found for request_id={} (final status {})",
                    businessRefId, finalStatus);
            return;
        }
        SesRequest request = found.get();
        request.setStatus(finalStatus.name());
        requestRepository.save(request);
    }
}
