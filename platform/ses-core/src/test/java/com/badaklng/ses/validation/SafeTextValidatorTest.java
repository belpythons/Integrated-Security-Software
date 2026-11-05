package com.badaklng.ses.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeTextValidatorTest {

    private SafeTextValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SafeTextValidator();
    }

    @Test
    void testNullAndEmptyAreValid() {
        assertTrue(validator.isValid(null, null), "Null string should be considered valid for SafeText (let @NotNull handle nullability)");
        assertTrue(validator.isValid("", null), "Empty string should be valid");
        assertTrue(validator.isValid("   ", null), "Whitespace string should be valid");
    }

    @Test
    void testSafePlainTextsAreValid() {
        assertTrue(validator.isValid("PT Badak NGL Bontang", null));
        assertTrue(validator.isValid("Pengajuan stiker dinas tahun 2026", null));
        assertTrue(validator.isValid("Nomor Polisi: KT 1234 BZ, Warna: Hitam Metalik", null));
        assertTrue(validator.isValid("Alamat: Jl. Brigjen Katamso No. 42, Telp: +62 812-3456-7890", null));
    }

    @Test
    void testScriptTagsAreBlocked() {
        assertFalse(validator.isValid("<script>alert('XSS')</script>", null));
        assertFalse(validator.isValid("<SCRIPT SRC='http://evil.com/xss.js'></SCRIPT>", null));
        assertFalse(validator.isValid("Hello <script> evil() </script> world", null));
        assertFalse(validator.isValid("</script>", null));
    }

    @Test
    void testJavascriptAndVbscriptProtocolsAreBlocked() {
        assertFalse(validator.isValid("javascript:alert(1)", null));
        assertFalse(validator.isValid("JAVASCRIPT: void(0);", null));
        assertFalse(validator.isValid("vbscript:msgbox(1)", null));
        assertFalse(validator.isValid("data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==", null));
    }

    @Test
    void testEventHandlersAreBlocked() {
        assertFalse(validator.isValid("<img src=x onerror=alert(1)>", null));
        assertFalse(validator.isValid("<svg onload=alert(1)>", null));
        assertFalse(validator.isValid("<body onload = alert(1)>", null));
        assertFalse(validator.isValid("<div onmouseover = 'malicious()'>", null));
    }

    @Test
    void testEmbeddedObjectsAreBlocked() {
        assertFalse(validator.isValid("<iframe src='http://attacker.com'></iframe>", null));
        assertFalse(validator.isValid("<object data='evil.swf'></object>", null));
        assertFalse(validator.isValid("<embed src='malicious.pdf'>", null));
    }
}
