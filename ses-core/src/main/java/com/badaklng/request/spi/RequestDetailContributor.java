package com.badaklng.request.spi;

import java.util.Map;

/**
 * Optional extra model attributes a business module wants merged into
 * request/view.html (the requestor's read-only request detail page) — e.g.
 * the scheduling module adding a "Scheduled Event" card. Discovered via
 * {@code ObjectProvider<RequestDetailContributor>} in RequestController so
 * the request module never gains a compile-time dependency on any business
 * module — same inversion {@code approval.spi.CheckpointTaskType} uses for
 * the chain editor's checkpoint-key dropdown.
 */
public interface RequestDetailContributor {

    /** Model attributes to merge in, keyed by attribute name. Return an empty map for nothing to show. */
    Map<String, Object> contribute(Long requestId);
}
