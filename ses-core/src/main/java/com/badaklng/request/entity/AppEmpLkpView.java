package com.badaklng.request.entity;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Read-only JPA entity over APP_EMP_LKP_VIEW — active employee master view
 * (from ELLORA.TBL_PERSON WHERE IS_ACTIVE = 1 UNION ADMIN).
 * <p>
 * This entity must never be persisted, updated, or deleted.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "APP_EMP_LKP_VIEW")
public class AppEmpLkpView {

    @Id
    @Column(name = "EMPLID", length = 10)
    private String emplid;

    @Column(name = "NAME_DISPLAY", length = 64)
    private String nameDisplay;

    @Column(name = "DEPT_ID", length = 10)
    private String deptId;

    @Column(name = "DEPT_DESC", length = 50)
    private String deptDesc;

    @Column(name = "SECTION_ID", length = 10)
    private String sectionId;

    @Column(name = "SECTION_DESC", length = 50)
    private String sectionDesc;

    @Column(name = "POSITION_NBR", length = 10)
    private String positionNbr;

    @Column(name = "POSITION_DESC", length = 128)
    private String positionDesc;

    @Column(name = "POS_REPORT_TO", length = 10)
    private String posReportTo;

    @Column(name = "POS_REPORT_TO_DESC", length = 25)
    private String posReportToDesc;

    @Column(name = "NAME_REPORT_TO")
    private String nameReportTo;

    @Column(name = "EMAIL", length = 70)
    private String email;

    @Column(name = "EMPLID_REPORT_TO")
    private String emplidReportTo;

    @Column(name = "EMP_STATUS", length = 1)
    private String empStatus;

    @Column(name = "EMP_STATUS_DESC", length = 8)
    private String empStatusDesc;

    @Column(name = "COST_CENTER")
    private String costCenter;

    @Column(name = "IS_PTB")
    private Integer isPtb;

    @Column(name = "IS_EXTENSION")
    private Integer isExtension;
}
