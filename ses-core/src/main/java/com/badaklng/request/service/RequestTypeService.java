package com.badaklng.request.service;

import com.badaklng.request.entity.SesDocRequirement;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.entity.SesRequestTypeDoc;
import com.badaklng.request.repository.SesDocRequirementRepository;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestTypeDocRepository;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.request.repository.SesRequestWorkerDocRepository;
import com.badaklng.platform.system.entity.AppUser;
import com.badaklng.platform.system.repository.AppUserRepository;
import com.badaklng.approval.entity.SesFormApprovalMap;
import com.badaklng.approval.repository.SesFormApprovalMapRepository;
import com.badaklng.approval.service.ApprovalChainService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CRUD for the Request Type Master admin screen — this screen is what makes
 * new request types and new document requirements a no-redeploy operation
 * (RequestApprovalBridge.matchKeys() reads SES_REQUEST_TYPE live).
 */
@Service
@RequiredArgsConstructor
public class RequestTypeService {

    private final SesRequestTypeRepository requestTypeRepository;
    private final SesRequestTypeDocRepository requestTypeDocRepository;
    private final SesDocRequirementRepository docRequirementRepository;
    private final SesRequestRepository requestRepository;
    private final SesRequestWorkerDocRepository requestWorkerDocRepository;
    private final AppUserRepository appUserRepository;
    private final SesFormApprovalMapRepository formApprovalMapRepository;
    private final ApprovalChainService approvalChainService;

    @Transactional(readOnly = true)
    public List<SesRequestType> findAll() {
        return requestTypeRepository.findAllByOrderByOrderByAsc();
    }

    @Transactional(readOnly = true)
    public SesRequestType findByCode(String typeCode) {
        return requestTypeRepository.findById(typeCode)
                .orElseThrow(() -> new IllegalArgumentException("Request type not found: " + typeCode));
    }

    @Transactional(readOnly = true)
    public Long getMappedChainId(String typeCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            return null;
        }
        return formApprovalMapRepository
                .findByFormCodeAndMatchKeyAndActiveFlag("SES_REQUEST", typeCode.trim().toUpperCase(), "Y")
                .map(SesFormApprovalMap::getChainId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean isInUse(String typeCode) {
        return requestRepository.existsByTypeCode(typeCode);
    }

    // ---------------------------------------------------------------
    // Per-type configuration read by other modules
    // ---------------------------------------------------------------

    /**
     * Does a submission of this type have to go through the chain's
     * Requestor's-Manager step?
     *
     * <p>An unknown type code answers {@code true}. The caller is about to
     * start an approval and will fail on the unknown code a moment later
     * anyway; until then the safe reading of "I do not know this type" is
     * "require the approval", never "skip it".</p>
     */
    @Transactional(readOnly = true)
    public boolean requiresManagerApproval(String typeCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            return true;
        }
        return requestTypeRepository.findById(typeCode.trim())
                .map(t -> !"N".equalsIgnoreCase(t.getNeedsMgrApprovalFlag()))
                .orElse(true);
    }

    /**
     * Does a submission of this type have to go through the chain's Manager
     * Procurement step?
     *
     * <p>Independent of {@link #requiresManagerApproval(String)} — the two
     * steps run in parallel, and switching one off leaves the other running.
     * Same fail-safe reading of an unknown type code: {@code true}.</p>
     */
    @Transactional(readOnly = true)
    public boolean requiresProcurementApproval(String typeCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            return true;
        }
        return requestTypeRepository.findById(typeCode.trim())
                .map(t -> !"N".equalsIgnoreCase(t.getNeedsProcApprovalFlag()))
                .orElse(true);
    }

    // ---------------------------------------------------------------
    // Auth-type visibility
    // ---------------------------------------------------------------

    /**
     * Whether a request type carrying {@code typeAuthType} may be seen and
     * chosen by a user whose {@code app_user.auth_type} is {@code userAuthType}.
     *
     * <p>Static and dependency-free on purpose, the same treatment
     * {@code ApprovalResolverService.decide} gets: the whole rule is one
     * testable function, with no Spring context and no Oracle behind it.</p>
     *
     * <p>Rules, in order:</p>
     * <ol>
     *   <li>A type marked {@code ALL} (also null or blank, which is what a row
     *       written before sql/48 looks like) is visible to everyone.</li>
     *   <li>Otherwise a user whose auth type is null, blank, or an unrecognised
     *       value sees NOTHING restricted — <b>fail closed</b>. Every real
     *       account has an auth type: {@code SesUserProvisioningProvider}
     *       writes {@code LDAP} on JIT provisioning, the platform's local path
     *       writes {@code LOCAL}, and sql/14 forces {@code LOCAL} in dev. A null
     *       is therefore an anomaly, and the safe reading of an anomaly is to
     *       grant the unrestricted set rather than the restricted one. Such a
     *       user still keeps every {@code ALL} type, which before this feature
     *       shipped was every type that existed.</li>
     *   <li>Otherwise exact, case-insensitive equality — matching the
     *       {@code "LDAP".equalsIgnoreCase(...)} idiom
     *       {@code SesUserProvisioningProvider} already uses, because
     *       {@code app_user.auth_type} is a plain VARCHAR2 with no enum behind
     *       it.</li>
     * </ol>
     */
    public static boolean visibleTo(String typeAuthType, String userAuthType) {
        String type = normalizeAuthType(typeAuthType);
        if (AUTH_TYPE_ALL.equals(type)) {
            return true;
        }
        if (userAuthType == null || userAuthType.trim().isEmpty()) {
            return false;
        }
        return type.equalsIgnoreCase(userAuthType.trim());
    }

    /** Collapses form input to one of {@code ALL}/{@code LOCAL}/{@code LDAP}; anything else becomes {@code ALL}. */
    public static String normalizeAuthType(String raw) {
        if (raw == null) {
            return AUTH_TYPE_ALL;
        }
        String v = raw.trim().toUpperCase();
        return (AUTH_TYPE_LOCAL.equals(v) || AUTH_TYPE_LDAP.equals(v)) ? v : AUTH_TYPE_ALL;
    }

    /** {@code app_user.auth_type} for this username, trimmed; null when the user is unknown. */
    @Transactional(readOnly = true)
    public String authTypeOf(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        return appUserRepository.findByUsername(username.trim())
                .map(AppUser::getAuthType)
                .orElse(null);
    }

    /**
     * The ACTIVE request types this user may choose, in {@code order_by} order
     * — the requestor-facing list, as opposed to {@link #findAll()}, which is
     * the admin master and must never be filtered.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "requestTypes", key = "#username")
    public List<SesRequestType> findSelectableFor(String username) {
        String userAuthType = authTypeOf(username);
        return requestTypeRepository.findByActiveFlagOrderByOrderByAsc("Y").stream()
                .filter(t -> visibleTo(t.getAuthType(), userAuthType))
                .collect(Collectors.toList());
    }

    /**
     * Server-side re-check behind the filtered dropdown. Without this, the
     * filter is cosmetic: anyone can POST a forbidden {@code typeCode}
     * straight at {@code /request/save}.
     *
     * <p>Note this keys off the CURRENT user, so under the switch-user filter
     * an admin impersonating a LOCAL account is held to that account's rules —
     * which is what makes impersonation useful for testing this.</p>
     *
     * @throws IllegalArgumentException when the type is unknown, inactive, or
     *         not visible to this user. Deliberately one message for all three:
     *         a requestor has no business learning which restricted types exist.
     */
    @Transactional(readOnly = true)
    public void assertSelectable(String typeCode, String username) {
        SesRequestType type = requestTypeRepository.findById(typeCode == null ? "" : typeCode.trim())
                .orElseThrow(() -> new IllegalArgumentException(NOT_SELECTABLE));
        if (!"Y".equalsIgnoreCase(type.getActiveFlag())
                || !visibleTo(type.getAuthType(), authTypeOf(username))) {
            throw new IllegalArgumentException(NOT_SELECTABLE);
        }
    }

    public static final String AUTH_TYPE_ALL = "ALL";
    public static final String AUTH_TYPE_LOCAL = "LOCAL";
    public static final String AUTH_TYPE_LDAP = "LDAP";

    private static final String NOT_SELECTABLE =
            "Jenis pengajuan ini tidak tersedia untuk akun Anda.";

    @Transactional
    @CacheEvict(value = {"requestTypes", "requestDocuments"}, allEntries = true)
    public void delete(String typeCode) {
        if (requestRepository.existsByTypeCode(typeCode)) {
            throw new IllegalStateException("Cannot delete: request type " + typeCode + " has existing requests.");
        }
        requestTypeDocRepository.deleteByTypeCode(typeCode);
        requestTypeRepository.deleteById(typeCode);
    }

    @Transactional(readOnly = true)
    public List<SesDocRequirement> findDocRequirements() {
        return docRequirementRepository.findAllByOrderByDocNameAsc();
    }

    /**
     * Doc catalog to offer on the request-type form: active docs, plus any
     * inactive doc still required by this type (so it stays visible/editable
     * there instead of silently vanishing) — inactive docs no longer clutter
     * every OTHER type's picker. Same list backs both the {@code allDocs}
     * table and {@link #buildDocSelectionsFor}, so their row indexes match.
     */
    @Transactional(readOnly = true)
    public List<SesDocRequirement> catalogFor(String typeCode) {
        Set<String> required = typeCode == null
                ? Collections.emptySet()
                : requestTypeDocRepository.findByTypeCodeOrderByOrderByAsc(typeCode).stream()
                        .map(SesRequestTypeDoc::getDocCode).collect(Collectors.toSet());
        return findDocRequirements().stream()
                .filter(d -> "Y".equals(d.getActiveFlag()) || required.contains(d.getDocCode()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "requestDocuments", key = "#typeCode")
    public List<SesRequestTypeDoc> findDocsForType(String typeCode) {
        return requestTypeDocRepository.findByTypeCodeOrderByOrderByAsc(typeCode);
    }

    /**
     * Upserts the type row, then replaces-by-diff its SesRequestTypeDoc rows
     * (deleteByTypeCode then reinsert) — simplest correct approach since
     * there's no ordering/identity to preserve across saves for this small
     * admin table. A DocSelection with no docCode but a docName (a document
     * the admin typed in via "+ Add Document" rather than picking from the
     * existing catalog) gets its SES_DOC_REQUIREMENT catalog row created
     * here first — the catalog is no longer a fixed, seed-only list.
     */
    @Transactional
    @CacheEvict(value = {"requestTypes", "requestDocuments"}, allEntries = true)
    public SesRequestType saveWithDocs(SesRequestType type, List<DocSelection> docSelections, boolean isCreate) {
        return saveWithDocs(type, docSelections, null, isCreate);
    }

    @Transactional
    @CacheEvict(value = {"requestTypes", "requestDocuments"}, allEntries = true)
    public SesRequestType saveWithDocs(SesRequestType type, List<DocSelection> docSelections, Long chainId, boolean isCreate) {
        if (type.getTypeCode() == null || type.getTypeCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Type Code is required.");
        }
        type.setTypeCode(type.getTypeCode().trim().toUpperCase());

        if (isCreate && requestTypeRepository.existsById(type.getTypeCode())) {
            throw new IllegalArgumentException("Type code already exists: " + type.getTypeCode());
        }

        // Integrity check: paying request types MUST have an approval chain with PAYMENT_VERIFICATION
        if ("Y".equalsIgnoreCase(type.getNeedsPaymentFlag())) {
            if (chainId == null) {
                throw new IllegalArgumentException("Jenis pengajuan berbayar wajib memilih Alur Persetujuan.");
            }
            if (!approvalChainService.hasPaymentCheckpoint(chainId)) {
                throw new IllegalArgumentException("Jenis pengajuan berbayar wajib memilih alur persetujuan yang memuat tahap verifikasi pembayaran (PAYMENT_VERIFICATION).");
            }
        }

        SesRequestType saved = requestTypeRepository.save(type);

        // Sync SES_FORM_APPROVAL_MAP if chainId is provided
        if (chainId != null) {
            SesFormApprovalMap map = formApprovalMapRepository
                    .findByFormCodeAndMatchKeyAndActiveFlag("SES_REQUEST", saved.getTypeCode(), "Y")
                    .orElseGet(() -> {
                        SesFormApprovalMap m = new SesFormApprovalMap();
                        m.setFormCode("SES_REQUEST");
                        m.setMatchKey(saved.getTypeCode());
                        m.setActiveFlag("Y");
                        return m;
                    });
            map.setChainId(chainId);
            formApprovalMapRepository.save(map);
        }

        requestTypeDocRepository.deleteByTypeCode(saved.getTypeCode());
        if (docSelections != null) {
            int order = 0;
            for (DocSelection sel : docSelections) {
                if (sel == null || !sel.isSelected()) {
                    continue;
                }
                String docCode = resolveDocCode(sel);
                if (docCode == null) {
                    continue;
                }
                SesRequestTypeDoc link = new SesRequestTypeDoc();
                link.setTypeCode(saved.getTypeCode());
                link.setDocCode(docCode);
                link.setMandatoryFlag(sel.isMandatory() ? "Y" : "N");
                link.setOrderBy(order++);
                requestTypeDocRepository.save(link);
            }
        }
        return saved;
    }

    /**
     * Returns the doc_code to link for this selection: the existing catalog
     * code if one was picked, or a freshly-created SES_DOC_REQUIREMENT row's
     * generated code if the admin typed a new document name in.
     */
    private String resolveDocCode(DocSelection sel) {
        if (sel.getDocCode() != null && !sel.getDocCode().trim().isEmpty()) {
            return sel.getDocCode().trim();
        }
        if (sel.getDocName() == null || sel.getDocName().trim().isEmpty()) {
            return null;
        }
        return docRequirementRepository.save(newDocRequirement(sel.getDocName().trim())).getDocCode();
    }

    /**
     * Removes a document from the SES_DOC_REQUIREMENT catalog — used to prune
     * docs added inline via "+ Add Document" that turned out unwanted, so the
     * catalog doesn't just grow and clutter every type's form forever. Hard
     * deletes when the doc was never actually wired to anything; otherwise
     * deactivates it (activeFlag=N) so it drops out of the picker for other
     * types while requests/uploads that already reference it are untouched.
     * Returns true if deactivated, false if hard-deleted.
     */
    @Transactional
    public boolean deleteDocRequirement(String docCode) {
        boolean inUse = requestTypeDocRepository.existsByDocCode(docCode)
                || requestWorkerDocRepository.existsByDocCode(docCode);
        if (inUse) {
            SesDocRequirement doc = docRequirementRepository.findById(docCode)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found: " + docCode));
            doc.setActiveFlag("N");
            docRequirementRepository.save(doc);
            return true;
        }
        docRequirementRepository.deleteById(docCode);
        return false;
    }

    @Transactional
    public void reactivateDocRequirement(String docCode) {
        SesDocRequirement doc = docRequirementRepository.findById(docCode)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + docCode));
        doc.setActiveFlag("Y");
        docRequirementRepository.save(doc);
    }

    /** Slugifies docName into a unique doc_code (max 30 chars, matching SES_DOC_REQUIREMENT.doc_code). */
    private SesDocRequirement newDocRequirement(String docName) {
        String base = docName.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "DOC";
        }
        if (base.length() > 26) {
            base = base.substring(0, 26);
        }
        String code = base;
        int suffix = 2;
        while (docRequirementRepository.existsById(code)) {
            String suffixStr = "_" + suffix;
            code = base.substring(0, Math.min(base.length(), 30 - suffixStr.length())) + suffixStr;
            suffix++;
        }
        SesDocRequirement doc = new SesDocRequirement();
        doc.setDocCode(code);
        doc.setDocName(docName);
        doc.setActiveFlag("Y");
        return doc;
    }

    /** One row of the admin screen's document-requirement table. */
    @Getter
    @Setter
    public static class DocSelection {
        private String docCode;
        private String docName;
        private boolean selected;
        private boolean mandatory = true;

        public DocSelection() {
        }

        public DocSelection(String docCode, String docName, boolean selected, boolean mandatory) {
            this.docCode = docCode;
            this.docName = docName;
            this.selected = selected;
            this.mandatory = mandatory;
        }
    }

    /** Convenience builder for the controller: full doc list with existing selections pre-checked. */
    @Transactional(readOnly = true)
    public List<DocSelection> buildDocSelectionsFor(String typeCode) {
        List<SesDocRequirement> allDocs = catalogFor(typeCode);
        List<SesRequestTypeDoc> existing = typeCode != null
                ? requestTypeDocRepository.findByTypeCodeOrderByOrderByAsc(typeCode)
                : new ArrayList<>();
        List<DocSelection> result = new ArrayList<>();
        for (SesDocRequirement doc : allDocs) {
            SesRequestTypeDoc match = existing.stream()
                    .filter(l -> l.getDocCode().equals(doc.getDocCode()))
                    .findFirst().orElse(null);
            result.add(new DocSelection(doc.getDocCode(), null, match != null, match != null && "Y".equals(match.getMandatoryFlag())));
        }
        return result;
    }
}
