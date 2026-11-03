package com.badaklng.ses.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class SesMailDispatcherServiceTest {

    @Test
    @DisplayName("referencesLogo detects various img tags pointing to ptb_logo.png")
    void testReferencesLogo() {
        assertTrue(SesMailDispatcherService.referencesLogo("<img src=\"/image/ptb_logo.png\">"));
        assertTrue(SesMailDispatcherService.referencesLogo("<img src='/image/ptb_logo.png'>"));
        assertTrue(SesMailDispatcherService.referencesLogo("<img src=\"http://host/ses/image/ptb_logo.png\">"));
        assertTrue(SesMailDispatcherService.referencesLogo("<img src=\"ptb_logo.png?v=2\">"));
        assertFalse(SesMailDispatcherService.referencesLogo("<p>Plain text</p>"));
        assertFalse(SesMailDispatcherService.referencesLogo("<img src=\"/image/other.png\">"));
        assertFalse(SesMailDispatcherService.referencesLogo(null));
    }

    @Test
    @DisplayName("normalizeLogoToHostedUrl converts img src to APP_LINK hosted web URL without CID attachment")
    void testNormalizeLogoToHostedUrl() {
        String appLink = "http://localhost:8082";
        String html = "<html><body><img src=\"/image/ptb_logo.png\" width=\"140\"></body></html>";
        String normalized = SesMailDispatcherService.normalizeLogoToHostedUrl(html, appLink);

        assertTrue(normalized.contains("src=\"http://localhost:8082/image/ptb_logo.png\""));
        assertFalse(normalized.contains("cid:badakLogo"), "Must NOT use CID inline attachment");
    }

    @Test
    @DisplayName("normalizeLogoToHostedUrl normalizes legacy production URLs with trailing slashes")
    void testNormalizeLogoWithLegacyUrl() {
        String appLink = "https://ses.badaklng.com/";
        String html = "<img src=\"http://apps.badaklng.home:8005/ses/image/ptb_logo.png\">";
        String normalized = SesMailDispatcherService.normalizeLogoToHostedUrl(html, appLink);

        assertTrue(normalized.contains("src=\"https://ses.badaklng.com/image/ptb_logo.png\""));
        assertFalse(normalized.contains("//image/ptb_logo.png"), "Must not have double slashes");
    }

    /**
     * The three timeouts JavaMail defaults to INFINITE. Without them, an SMTP
     * port that a firewall DROPs instead of REJECTing hangs the sending thread
     * forever, and because the drain is single-flight that one hang stalls
     * every other mail behind it. This test exists because the regression is
     * invisible: mail keeps working right up until the relay goes quiet.
     */
    @Test
    @DisplayName("buildSender bounds connect, read and write timeouts")
    void testSmtpTimeoutsAreSet() {
        Properties jp = SesMailDispatcherService
                .buildSender("host=10.10.1.8,port=25,starttls=true")
                .getJavaMailProperties();

        assertEquals("10000", jp.getProperty("mail.smtp.connectiontimeout"),
                "an unset connect timeout waits forever on a black-holed port");
        assertEquals("10000", jp.getProperty("mail.smtp.timeout"),
                "an unset read timeout waits forever mid-conversation");
        assertEquals("10000", jp.getProperty("mail.smtp.writetimeout"),
                "an unset write timeout waits forever on a stalled socket");
    }

    @Test
    @DisplayName("buildSender honours a timeout= override in MAIL_SERVER")
    void testSmtpTimeoutOverride() {
        Properties jp = SesMailDispatcherService
                .buildSender("host=mail.example.com,port=587,timeout=2500")
                .getJavaMailProperties();

        assertEquals("2500", jp.getProperty("mail.smtp.connectiontimeout"));
        assertEquals("2500", jp.getProperty("mail.smtp.timeout"));
        assertEquals("2500", jp.getProperty("mail.smtp.writetimeout"));
    }

    @Test
    @DisplayName("buildSender keeps SMTP auth off when MAIL_SERVER carries no credentials")
    void testNoAuthWithoutCredentials() {
        JavaMailSenderImpl sender =
                SesMailDispatcherService.buildSender("host=10.10.1.8,port=25,user=,password=,starttls=true");

        assertNull(sender.getJavaMailProperties().getProperty("mail.smtp.auth"),
                "blank user/password must not turn auth on -- that is the shape ELLDEV's MAIL_SERVER uses");
        assertEquals("10.10.1.8", sender.getHost());
        assertEquals(25, sender.getPort());
    }

    @Test
    @DisplayName("isHtml correctly identifies markup vs plain text")
    void testIsHtml() {
        assertTrue(SesMailDispatcherService.isHtml("<html><body>Hi</body></html>"));
        assertTrue(SesMailDispatcherService.isHtml("   <div>Hello</div>"));
        assertFalse(SesMailDispatcherService.isHtml("Dear User,\n\nPlease see attached."));
        assertFalse(SesMailDispatcherService.isHtml(null));
    }
}
