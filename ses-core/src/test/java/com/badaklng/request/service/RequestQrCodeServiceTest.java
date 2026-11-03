package com.badaklng.request.service;

import com.badaklng.platform.system.service.AppParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestQrCodeServiceTest {

    @Mock
    private AppParameterService appParameterService;

    private RequestQrCodeService qrCodeService;

    @BeforeEach
    void setUp() {
        qrCodeService = new RequestQrCodeService(appParameterService);
        ReflectionTestUtils.setField(qrCodeService, "fallbackBaseUrl", "http://localhost:8082");
    }

    @Test
    @DisplayName("verifyUrl prioritizes APP_LINK_EXT when configured")
    void testVerifyUrlUsesAppLinkExt() {
        when(appParameterService.getValue("APP_LINK_EXT", ""))
                .thenReturn("https://ses-ext.badaklng.co.id/ses");

        String url = qrCodeService.verifyUrl("REQ-2026-0001");
        assertEquals("https://ses-ext.badaklng.co.id/ses/verify/REQ-2026-0001", url);
    }

    @Test
    @DisplayName("verifyUrl falls back to APP_LINK when APP_LINK_EXT is blank")
    void testVerifyUrlFallsBackToAppLink() {
        when(appParameterService.getValue("APP_LINK_EXT", ""))
                .thenReturn("");
        when(appParameterService.getValue("APP_LINK", ""))
                .thenReturn("http://apps.badaklng.home:8005/ses");

        String url = qrCodeService.verifyUrl("REQ-2026-0001");
        assertEquals("http://apps.badaklng.home:8005/ses/verify/REQ-2026-0001", url);
    }

    @Test
    @DisplayName("verifyUrl falls back to fallbackBaseUrl when both parameters are blank")
    void testVerifyUrlFallsBackToBaseUrl() {
        when(appParameterService.getValue("APP_LINK_EXT", ""))
                .thenReturn(null);
        when(appParameterService.getValue("APP_LINK", ""))
                .thenReturn("  ");

        String url = qrCodeService.verifyUrl("REQ-2026-0001");
        assertEquals("http://localhost:8082/verify/REQ-2026-0001", url);
    }

    @Test
    @DisplayName("verifyUrl strips trailing slashes properly")
    void testVerifyUrlStripsTrailingSlash() {
        when(appParameterService.getValue("APP_LINK_EXT", ""))
                .thenReturn("https://ses-ext.badaklng.co.id/ses/");

        String url = qrCodeService.verifyUrl("REQ-2026-0001");
        assertEquals("https://ses-ext.badaklng.co.id/ses/verify/REQ-2026-0001", url);
    }

    @Test
    @DisplayName("pngFor and pngDataUriFor generate valid QR image bytes")
    void testPngGeneration() {
        when(appParameterService.getValue("APP_LINK_EXT", ""))
                .thenReturn("https://ses-ext.badaklng.co.id");

        byte[] png = qrCodeService.pngFor("REQ-2026-0001");
        assertNotNull(png);
        assertTrue(png.length > 0);

        String dataUri = qrCodeService.pngDataUriFor("REQ-2026-0001");
        assertNotNull(dataUri);
        assertTrue(dataUri.startsWith("data:image/png;base64,"));
    }
}
