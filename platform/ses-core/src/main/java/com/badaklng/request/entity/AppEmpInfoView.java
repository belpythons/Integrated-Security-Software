package com.badaklng.request.entity;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Read-only employee master view used for employee identity and approval
 * routing. Approval routing uses PB_SECTION when present, otherwise DEPTID.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "APP_EMP_INFO_VIEW")
public class AppEmpInfoView {

    @Id
    @Column(name = "EMPLID", length = 11)
    private String emplid;

    @Column(name = "NAME_DISPLAY", length = 50)
    private String nameDisplay;

    @Column(name = "DEPTID", length = 10)
    private String deptId;

    @Column(name = "PB_SECTION", length = 10)
    private String pbSection;

    @Column(name = "POSITION_NBR", length = 10)
    private String positionNbr;

    @Column(name = "POS_REPORT_TO", length = 10)
    private String posReportTo;
}
