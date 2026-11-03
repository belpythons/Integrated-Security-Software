package com.badaklng.approval.entity;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite id for APP_POSITION_VIEW: POS_ID alone is not unique since
 * several people can be assigned to the same position at once — POS_ID
 * plus EMPL_ID identifies a single row.
 */
@EqualsAndHashCode
@NoArgsConstructor
public class AppPositionViewId implements Serializable {

    private String posId;
    private String emplId;

    public AppPositionViewId(String posId, String emplId) {
        this.posId = posId;
        this.emplId = emplId;
    }
}
