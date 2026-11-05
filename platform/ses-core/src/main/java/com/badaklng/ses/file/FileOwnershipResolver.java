package com.badaklng.ses.file;

/**
 * SPI implemented by business modules that own uploaded files (e.g. the request
 * module for {@code SES_REQUEST_WORKER_DOC:*} refIds). Collected by
 * {@link AppFileController} via {@code ObjectProvider<FileOwnershipResolver>} so
 * that the file module has zero compile-time dependency on any module that owns
 * uploaded files — the same loose-coupling pattern the approval module uses for
 * form descriptors.
 * <p>
 * The first registered resolver whose {@link #supports(String)} returns true for a
 * given {@code refId} decides access; the controller never falls through to another
 * resolver, and denies access by default when no resolver claims the refId.
 */
public interface FileOwnershipResolver {

    /** true if this resolver knows how to interpret refId's owning table. */
    boolean supports(String refId);

    /** true if the given user may download the file identified by refId. */
    boolean canAccess(String refId, String username, String userId);
}
