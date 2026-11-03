package com.badaklng.ses.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gives {@link AccessDeniedException} and the business-rule rejections
 * ({@link IllegalStateException}, {@link IllegalArgumentException}) their own
 * HTTP status, instead of falling through to platform's
 * {@code GlobalExceptionHandler#handleGeneral} — a catch-all that always
 * answers 500, even for "you can't cancel someone else's request" or "this
 * request is already approved".
 *
 * <p><b>Why this was ever a 500 in the first place.</b> Platform's own
 * {@code GlobalExceptionHandler} already has a dedicated
 * {@code @ExceptionHandler(AccessDeniedException.class)} that renders
 * {@code error/403} with the right status — so the 500 wasn't a missing
 * handler. It was {@code layout/navbar.html} (SES's own fragment, included by
 * every {@code error/*} page through {@code layout/base}) indexing
 * {@code pageActions['/switchuser']} and {@code appParams['APP_PHOTO_URL']}
 * without checking the map itself was non-null first. Those two model
 * attributes come from platform's {@code GlobalModelAttributes}
 * {@code @ModelAttribute} methods, which Spring only runs for a NORMAL
 * controller dispatch — not for exception resolution via
 * {@code @ExceptionHandler}, which is how an exception thrown deep inside a
 * service call (e.g. {@code RequestService#cancel}'s {@code assertOwned})
 * reaches here. So the view model arrived with both maps simply absent, the
 * SpEL indexing threw {@code EL1012E: Cannot index into a null value} while
 * rendering, and that SECOND, unhandled exception is what Tomcat reported as
 * a plain 500 — overwriting the 403 platform's handler had already set.
 * Fixed at the source in {@code navbar.html}; this class exists for the
 * status-code precision Task 1 asked for, not to route around that bug.</p>
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} so these three exception types always
 * resolve here rather than in platform's advice, regardless of bean
 * registration order.</p>
 *
 * <p>AJAX/fetch callers (no {@code text/html} in {@code Accept}, or
 * {@code X-Requested-With: XMLHttpRequest}) get a small JSON body instead of
 * a full page — a full HTML page assumes a human is looking at the tab it
 * renders into.</p>
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SesExceptionHandler {

    private final ObjectMapper objectMapper;

    public SesExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex, HttpServletRequest request,
                                     HttpServletResponse response, Model model) throws IOException {
        return respond(request, response, model, HttpStatus.FORBIDDEN, "error/403", "Access Denied", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException ex,
                                      HttpServletRequest request, HttpServletResponse response, Model model) throws IOException {
        String message = "Ukuran berkas melebihi batas maksimum 10 MB.";
        return respond(request, response, model, HttpStatus.BAD_REQUEST, "error/400", "File Too Large", message);
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public String handleBadRequest(RuntimeException ex, HttpServletRequest request,
                                   HttpServletResponse response, Model model) throws IOException {
        return respond(request, response, model, HttpStatus.BAD_REQUEST, "error/400", "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public String handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                               HttpServletRequest request, HttpServletResponse response, Model model) throws IOException {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return respondValidation(request, response, model, "Validasi formulir gagal", fieldErrors);
    }

    @ExceptionHandler(BindException.class)
    public String handleBindException(BindException ex,
                                      HttpServletRequest request, HttpServletResponse response, Model model) throws IOException {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return respondValidation(request, response, model, "Validasi formulir gagal", fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public String handleConstraintViolation(ConstraintViolationException ex,
                                            HttpServletRequest request, HttpServletResponse response, Model model) throws IOException {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            fieldErrors.put(cv.getPropertyPath().toString(), cv.getMessage());
        }
        return respondValidation(request, response, model, "Validasi parameter gagal", fieldErrors);
    }

    private String respondValidation(HttpServletRequest request, HttpServletResponse response, Model model,
                                     String message, Map<String, String> fieldErrors) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        if (wantsJson(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", java.time.Instant.now().toString());
            body.put("status", HttpStatus.BAD_REQUEST.value());
            body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
            body.put("message", message);
            body.put("fieldErrors", fieldErrors);
            body.put("path", request.getRequestURI());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), body);
            return null;
        }
        model.addAttribute("pageTitle", "Validasi Gagal");
        StringBuilder sb = new StringBuilder(message).append(":\n");
        fieldErrors.forEach((field, msg) -> sb.append("• ").append(field).append(": ").append(msg).append("\n"));
        model.addAttribute("errorMessage", sb.toString());
        model.addAttribute("fieldErrors", fieldErrors);
        return "error/400";
    }

    /**
     * @return the view name to render, or {@code null} when the JSON branch
     *         already wrote the full response body itself (the convention
     *         Spring MVC uses for "nothing more to do" from a String-returning
     *         handler method).
     */
    private String respond(HttpServletRequest request, HttpServletResponse response, Model model,
                           HttpStatus status, String view, String title, String message) throws IOException {
        response.setStatus(status.value());
        if (wantsJson(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", java.time.Instant.now().toString());
            body.put("status", status.value());
            body.put("error", status.getReasonPhrase());
            body.put("message", message);
            body.put("path", request.getRequestURI());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), body);
            return null;
        }
        model.addAttribute("pageTitle", title);
        model.addAttribute("errorMessage", message);
        return view;
    }

    private boolean wantsJson(HttpServletRequest request) {
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }
}
