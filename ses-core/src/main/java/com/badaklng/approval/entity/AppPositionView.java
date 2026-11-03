package com.badaklng.approval.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/**
 * Read-only JPA view of APP_POSITION_VIEW.
 *
 * IMPORTANT: In production this is a VIEW owned by another schema/app
 * and is READ-ONLY for SES. This entity must never be persisted,
 * updated, or deleted. {@code holder_empl_id} is the PJS-aware holder
 * (PJS if the PJS date range covers today, else the definitive empl).
 *
 * A single POS_ID is not unique in this view: several people can be
 * assigned to the same position id at once (co-officers), so each
 * assignment is its own row and POS_ID+EMPL_ID together identify a row.
 */
@Getter
@Setter
@Entity
@IdClass(AppPositionViewId.class)
@Table(name = "APP_POSITION_VIEW")
public class AppPositionView {

    @Id
    @Column(name = "POS_ID", length = 10)
    private String posId;

    @Column(name = "POS_TITLE", length = 100)
    private String posTitle;

    @Column(name = "SUPERIOR_ID", length = 10)
    private String superiorId;

    @Id
    @Column(name = "EMPL_ID", length = 10)
    private String emplId;

    @Column(name = "EMPL_NAME", length = 100)
    private String emplName;

    @Column(name = "DEPT_ID", length = 10)
    private String deptId;

    @Column(name = "SECTION_ID", length = 10)
    private String sectionId;

    @Column(name = "DEPT_DESC", length = 100)
    private String deptDesc;

    @Column(name = "SECTION_DESC", length = 100)
    private String sectionDesc;

    @Column(name = "EMPL_EMAIL", length = 100)
    private String emplEmail;

    @Column(name = "PJS_EMPL_ID", length = 10)
    private String pjsEmplId;

    @Column(name = "PJS_EMPL_NAME", length = 100)
    private String pjsEmplName;

    @Column(name = "PJS_EMAIL", length = 100)
    private String pjsEmail;

    @Column(name = "START_DATE")
    private java.sql.Date startDate;

    @Column(name = "END_DATE")
    private java.sql.Date endDate;

    /** PJS-aware current holder (PJS if active, else empl). */
    @Column(name = "HOLDER_EMPL_ID", length = 10)
    private String holderEmplId;

    @Column(name = "HOLDER_EMPL_NAME", length = 100)
    private String holderEmplName;

    @Column(name = "HOLDER_EMAIL", length = 100)
    private String holderEmail;

    /**
     * Convenience: is this position currently held by someone?
     */
    @Transient
    public boolean hasHolder() {
        return holderEmplId != null && !holderEmplId.trim().isEmpty();
    }
}
