package com.badaklng.ses.mail;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SES ships no logo of its own — {@code /image/ptb_logo.png} comes from the
 * platform jar, and SesMailJob attaches that exact file as the inline
 * {@code cid:badakLogo} part.
 *
 * <p>Deleting SES's own copy is what fixed the oversized login logo, but it
 * also moved the asset out of this repository, where nothing here would notice
 * it disappearing. A platform bump that drops or renames the file would leave
 * every approval mail with a logo-shaped hole and no failing build. This is
 * that missing alarm; no Spring context, it is one classpath lookup.</p>
 */
class SesMailJobLogoTest {

    @Test
    void logoIsOnTheClasspath() {
        assertTrue(new ClassPathResource("static/image/ptb_logo.png").exists(),
                "static/image/ptb_logo.png is missing — approval mails would ship a broken inline logo");
    }
}
