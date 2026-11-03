package com.badaklng.sticker.model;

import com.badaklng.ses.validation.DraftGroup;
import com.badaklng.ses.validation.SubmitGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StickerEditFormValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testDraftGroupAllowsMinimalInput() {
        StickerEditForm draft = new StickerEditForm();
        draft.setBadge("B-1234");
        // Name and vehicles are empty, which is allowed in DraftGroup
        Set<ConstraintViolation<StickerEditForm>> violations = validator.validate(draft, DraftGroup.class);
        assertTrue(violations.isEmpty(), "DraftGroup should allow empty requestorName and vehicles");
    }

    @Test
    void testDraftGroupRejectsInvalidBadgePattern() {
        StickerEditForm draft = new StickerEditForm();
        draft.setBadge("BADGE#@$%!"); // Invalid characters
        Set<ConstraintViolation<StickerEditForm>> violations = validator.validate(draft, DraftGroup.class);
        assertFalse(violations.isEmpty(), "DraftGroup must reject invalid badge format");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("badge")));
    }

    @Test
    void testSubmitGroupRejectsMissingFields() {
        StickerEditForm form = new StickerEditForm();
        form.setRequestorName("");
        form.setBadge("");
        form.setVehicles(Collections.emptyList());

        Set<ConstraintViolation<StickerEditForm>> violations = validator.validate(form, SubmitGroup.class);
        assertFalse(violations.isEmpty(), "SubmitGroup must reject empty form");

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("requestorName")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("badge")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("vehicles")));
    }

    @Test
    void testSubmitGroupRejectsXssInFields() {
        StickerEditForm form = new StickerEditForm();
        form.setRequestorName("John <script>alert('pwned')</script>");
        form.setBadge("12345");
        form.setAddress("<iframe src='http://evil.com'></iframe>");

        StickerEditForm.VehicleRow v = new StickerEditForm.VehicleRow();
        v.setStickerTypeCode("DINAS");
        v.setPoliceNo("KT 1234 BZ");
        v.setVehicleType("Sedan <img src=x onerror=alert(1)>");
        form.setVehicles(Collections.singletonList(v));

        Set<ConstraintViolation<StickerEditForm>> violations = validator.validate(form, SubmitGroup.class);
        assertFalse(violations.isEmpty(), "XSS payloads must be rejected by @SafeText");

        assertTrue(violations.stream().anyMatch(vi -> vi.getPropertyPath().toString().equals("requestorName")));
        assertTrue(violations.stream().anyMatch(vi -> vi.getPropertyPath().toString().equals("address")));
        assertTrue(violations.stream().anyMatch(vi -> vi.getPropertyPath().toString().contains("vehicleType")));
    }

    @Test
    void testVehicleRowValidation() {
        StickerEditForm.VehicleRow row = new StickerEditForm.VehicleRow();
        row.setDriverName("");
        row.setPoliceNo("KT@!#$INVALID");
        row.setYearMade("abcd"); // Invalid year format

        Set<ConstraintViolation<StickerEditForm.VehicleRow>> violations = validator.validate(row, SubmitGroup.class);
        assertFalse(violations.isEmpty(), "Invalid vehicle row must fail validation");

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("driverName")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("policeNo")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("yearMade")));
    }

    @Test
    void testValidSubmitGroupPasses() {
        StickerEditForm form = new StickerEditForm();
        form.setStickerTypeCode("DINAS");
        form.setRequestorName("Ahmad Dahlan");
        form.setBadge("BADAK-9921");
        form.setDepartment("Information Technology");
        form.setSection("Software Engineering");
        form.setPhoneNo("+62812345678");
        form.setAddress("Komplek PT Badak NGL Blok A-12");

        StickerEditForm.VehicleRow v = new StickerEditForm.VehicleRow();
        v.setDriverName("Budi Santoso");
        v.setDriverBadge("BADAK-9921");
        v.setPoliceNo("KT 8821 BZ");
        v.setVehicleType("SUV");
        v.setBrand("Toyota Fortuner");
        v.setColor("Hitam");
        v.setYearMade("2023");
        form.setVehicles(Collections.singletonList(v));

        Set<ConstraintViolation<StickerEditForm>> violations = validator.validate(form, SubmitGroup.class);
        assertTrue(violations.isEmpty(), "Clean valid form must pass all SubmitGroup constraints");
    }
}
