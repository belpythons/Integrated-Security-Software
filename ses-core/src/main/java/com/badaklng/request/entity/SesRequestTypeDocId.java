package com.badaklng.request.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite PK class for {@link SesRequestTypeDoc}, mirroring platform's
 * AppCatId pattern (used with AppCat's @IdClass(AppCatId.class)): a plain
 * Serializable holder with a no-arg constructor plus equals()/hashCode()
 * over exactly the two @Id fields.
 */
public class SesRequestTypeDocId implements Serializable {

    private String typeCode;
    private String docCode;

    public SesRequestTypeDocId() {
    }

    public SesRequestTypeDocId(String typeCode, String docCode) {
        this.typeCode = typeCode;
        this.docCode = docCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SesRequestTypeDocId)) return false;
        SesRequestTypeDocId that = (SesRequestTypeDocId) o;
        return Objects.equals(typeCode, that.typeCode) && Objects.equals(docCode, that.docCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeCode, docCode);
    }
}
