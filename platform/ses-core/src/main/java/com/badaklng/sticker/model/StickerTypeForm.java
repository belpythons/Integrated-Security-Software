package com.badaklng.sticker.model;

import com.badaklng.platform.model.BaseForm;
import com.badaklng.sticker.entity.SesStickerType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Web binding target for /admin/sticker-type's create/edit form. */
@Getter
@Setter
@NoArgsConstructor
public class StickerTypeForm extends BaseForm {

    public static final String LIST_URL = "/admin/sticker-type";
    public static final String FORM_URL = "/admin/sticker-type/form";

    /** Null (new code entered by hand) on create. */
    private String stickerTypeCode;
    private String stickerTypeName;
    /** IDR, whole Rupiah. */
    private Long price = 0L;
    private Integer orderBy = 0;
    private String activeFlag = "Y";
    private boolean isEdit;

    public static StickerTypeForm from(SesStickerType type) {
        StickerTypeForm f = new StickerTypeForm();
        f.setStickerTypeCode(type.getStickerTypeCode());
        f.setStickerTypeName(type.getStickerTypeName());
        f.setPrice(type.getPrice());
        f.setOrderBy(type.getOrderBy());
        f.setActiveFlag(type.getActiveFlag());
        f.setEdit(true);
        return f;
    }

    public SesStickerType toEntity() {
        SesStickerType type = new SesStickerType();
        type.setStickerTypeCode(stickerTypeCode);
        type.setStickerTypeName(stickerTypeName);
        type.setPrice(price != null ? price : 0L);
        type.setOrderBy(orderBy != null ? orderBy : 0);
        type.setActiveFlag("Y".equalsIgnoreCase(activeFlag) ? "Y" : "N");
        return type;
    }
}
