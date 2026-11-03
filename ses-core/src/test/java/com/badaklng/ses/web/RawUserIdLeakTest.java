package com.badaklng.ses.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sweeps every Thymeleaf template for an expression that would render a raw
 * {@code APP_USER.USER_ID} at a reader.
 *
 * <p>A sweep rather than two assertions about the two screens that were
 * actually wrong. Those two are fixed either way; what this stops is the
 * THIRD one, written six months from now by somebody who has a
 * {@code ...UserId} field in a DTO and no reason to suspect it holds a UUID.
 * That is exactly how both of the fixed ones happened.</p>
 *
 * <p><b>Why this is confusing enough to need writing down.</b> In this schema
 * two differently-named things both look like a user reference, and only one
 * of them is a UUID:</p>
 * <ul>
 *   <li>{@code SesRequest.requestorUserId} and
 *       {@code SesApprovalInstance.requestorUserId} hold the <b>username</b>,
 *       despite the name. Rendering those is fine — the username IS the badge
 *       id here — and several screens deliberately show one next to the
 *       resolved full name.</li>
 *   <li>{@code SesApprovalHistory.actorUserId},
 *       {@code SesRequestScheduleHistory.actorUserId},
 *       {@code SesRequestPaymentHistory.actorUserId},
 *       {@code SesApprovalTask.completedBy} and
 *       {@code SesRequestPayment.reviewedBy} hold the
 *       <b>{@code APP_USER.USER_ID} UUID</b>. Rendering one of those puts
 *       {@code 00000000-0000-0000-0000-000000000001} on screen.</li>
 * </ul>
 *
 * <p>So the rule cannot be "no field ending in UserId". It is: the UUID-valued
 * references must always go through a resolved-name map. {@code displayNames}
 * never returns null or blank for a non-blank input, so an
 * {@code ?: rawReference} fallback is permitted — it only fires for an actor
 * whose {@code APP_USER} row has been deleted, where the raw value is better
 * than an empty cell.</p>
 */
class RawUserIdLeakTest {

    private static final Path TEMPLATES = resolveTemplates();

    private static Path resolveTemplates() {
        Path direct = Paths.get("src", "main", "resources", "templates");
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromCore = Paths.get("..", "ses-app", "src", "main", "resources", "templates");
        if (Files.exists(fromCore)) {
            return fromCore;
        }
        Path fromRoot = Paths.get("ses-app", "src", "main", "resources", "templates");
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        return direct;
    }

    /**
     * Expressions that are UUID-valued. Each is matched inside a {@code th:text}
     * or {@code th:utext}, i.e. only where it would become visible text.
     */
    private static final String[] UUID_REFS = {
            "actorUserId",
            "completedBy",
            "reviewedBy",
            "requestorId",
    };

    /** {@code th:text="${...}"} / {@code th:utext="${...}"} and what is inside. */
    private static final Pattern RENDERED = Pattern.compile(
            "th:u?text\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private static List<Path> templates() throws IOException {
        try (Stream<Path> walk = Files.walk(TEMPLATES)) {
            List<Path> out = new ArrayList<>();
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".html"))
                    .forEach(out::add);
            return out;
        }
    }

    @Test
    @DisplayName("No template renders a UUID-valued user reference as bare text")
    void noRawUuidReferenceIsRendered() throws IOException {
        List<String> offences = new ArrayList<>();

        for (Path file : templates()) {
            String html = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Matcher m = RENDERED.matcher(html);
            while (m.find()) {
                String expr = m.group(1);
                for (String ref : UUID_REFS) {
                    // Whole property name only. A substring match flags
                    // ${r.reviewedByName} — which is the RESOLVED name, built
                    // in PaymentRecapController.toRows — as if it were the raw
                    // reviewedBy reference. \b does not fire inside
                    // "reviewedByName", so the word boundary is what tells the
                    // two apart.
                    if (!Pattern.compile("\\b" + ref + "\\b").matcher(expr).find()) {
                        continue;
                    }
                    // A resolved-name map in the same expression is the whole
                    // point: historyActorNames[h.actorUserId], or
                    // completedByNames[t.taskId]. The raw reference may appear
                    // as the ?: fallback of such a lookup.
                    boolean resolved = expr.contains("Names[") || expr.contains("Names.get(")
                            || expr.contains("displayName") || expr.contains("Display");
                    if (!resolved) {
                        offences.add(TEMPLATES.relativize(file) + "  ->  th:text=\"" + expr + "\"");
                    }
                }
            }
        }

        assertTrue(offences.isEmpty(),
                "These expressions would render an APP_USER.USER_ID (a UUID) on screen.\n"
                        + "Resolve it through UserDisplayNameService and render the name instead\n"
                        + "(batch the column with displayNames(...), not one call per row):\n  "
                        + String.join("\n  ", offences));
    }

    // ---------------------------------------------------------------
    // The two screens that were actually wrong
    // ---------------------------------------------------------------

    @Test
    @DisplayName("approval/my.html no longer has a Requestor ID column")
    void approvalMyDroppedTheIdColumn() throws IOException {
        String html = read("approval/my.html");

        assertFalse(html.contains("Requestor ID"), "the UUID column header must be gone");
        assertFalse(html.contains("requestorId"),
                "and so must the expression — the field itself was removed from ApprovalTaskForm");
        assertTrue(html.contains("${r.requestorName}"), "the resolved name is what remains");
    }

    @Test
    @DisplayName("scheduling/manage.html resolves its history actor")
    void schedulingManageResolvesActor() throws IOException {
        // This was the worst of the two: unlike every other screen it had no
        // fallback at all, because SchedulingController injected
        // UserDisplayNameService and used it in calendar() and queue() but
        // never in manage().
        String html = read("scheduling/manage.html");

        assertTrue(html.contains("historyActorNames"),
                "the By column must go through the resolved-name map");
    }

    @Test
    @DisplayName("Screens that show a username next to a name keep doing so")
    void pairedUsernameColumnsSurvive() throws IOException {
        // requestorUserId holds the USERNAME (the badge id), not a UUID. These
        // columns were asked for and must not be swept away by an
        // over-enthusiastic reading of "remove the UID columns".
        assertTrue(read("dashboard/index.html").contains("${r.requestorUserId}")
                || read("dashboard/index.html").contains("${t.requestorUserId}"));
        assertTrue(read("payment/recap.html").contains("${r.requestorUserId}"));
        assertTrue(read("scheduling/queue.html").contains("${r.requestorUserId}"));
    }

    @Test
    @DisplayName("The sweep actually looks at a meaningful number of templates")
    void sweepIsNotVacuous() throws IOException {
        // A regex that silently stops matching, or a path that stops
        // resolving, would make the main test above pass by finding nothing.
        List<Path> all = templates();
        assertTrue(all.size() > 30, "only found " + all.size() + " templates — the sweep path looks wrong");
    }

    private static String read(String relative) throws IOException {
        Path p = TEMPLATES.resolve(relative);
        assertTrue(Files.exists(p), p + " is missing");
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
}
