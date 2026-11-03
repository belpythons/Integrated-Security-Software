package com.badaklng.request.model;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RequestFormValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testBlankTypeCodeAndInvalidEmail() {
        RequestEditForm form = new RequestEditForm();
        form.setTypeCode("");
        form.setEmail("invalid-email-address");

        Set<ConstraintViolation<RequestEditForm>> violations = validator.validate(form);
        assertFalse(violations.isEmpty(), "Form with blank type and bad email must fail validation");

        boolean hasTypeCodeViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("typeCode"));
        boolean hasEmailViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("email"));

        assertTrue(hasTypeCodeViolation, "typeCode must have a constraint violation");
        assertTrue(hasEmailViolation, "email must have a constraint violation");
    }

    @Test
    void testWorkerRowValidation() {
        RequestEditForm.WorkerRow worker = new RequestEditForm.WorkerRow();
        worker.setWorkerName("");
        worker.setNik("1"); // too short (< 3)
        worker.setGender("");

        Set<ConstraintViolation<RequestEditForm.WorkerRow>> violations = validator.validate(worker);
        assertFalse(violations.isEmpty());

        boolean hasNameViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("workerName"));
        boolean hasNikViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("nik"));
        boolean hasGenderViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("gender"));

        assertTrue(hasNameViolation, "workerName must fail when blank");
        assertTrue(hasNikViolation, "nik must fail when length < 3");
        assertTrue(hasGenderViolation, "gender must fail when blank");
    }

    @Test
    void testValidFormAndWorker() {
        RequestEditForm form = new RequestEditForm();
        form.setTypeCode("BADGE_BARU");
        form.setEmail("contractor@vendor.com");

        RequestEditForm.WorkerRow worker = new RequestEditForm.WorkerRow();
        worker.setWorkerName("Budi Santoso");
        worker.setNik("6472010101900001");
        worker.setGender("L");

        form.setWorkers(Collections.singletonList(worker));

        Set<ConstraintViolation<RequestEditForm>> violations = validator.validate(form);
        assertTrue(violations.isEmpty(), "Valid form and worker should have 0 violations: " + violations);
    }
}
