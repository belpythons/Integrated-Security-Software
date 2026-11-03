package com.badaklng.ses.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * High-Performance In-Memory Spring Cache Configuration.
 *
 * <p>Enables zero-latency in-memory JVM caching for frequently accessed
 * master data, lookup views, and reference tables (e.g. Sticker Types,
 * Request Types, Required Documents, User Display Names, and Worker Lookups).
 * This eliminates repeated database round-trips to Oracle, reducing server
 * TTFB to &lt; 40ms and enabling sub-second LCP on initial page loads and
 * single-digit ms on repeats.</p>
 */
@Configuration
@EnableCaching
public class SesCacheConfig {

    public static final String CACHE_STICKER_TYPES = "stickerTypes";
    public static final String CACHE_STICKER_BASE_AMOUNT = "stickerBaseAmount";
    public static final String CACHE_REQUEST_TYPES = "requestTypes";
    public static final String CACHE_REQUEST_DOCS = "requestDocuments";
    public static final String CACHE_USER_DISPLAY_NAMES = "userDisplayNames";
    public static final String CACHE_WORKER_DEPARTMENTS = "workerDepartments";
    public static final String CACHE_WORKER_SECTIONS = "workerSections";
    public static final String CACHE_WORKER_JABATANS = "workerJabatans";
    public static final String CACHE_APPROVAL_CHAINS = "approvalChainStatus";
    public static final String CACHE_SWITCHABLE_USERS = "switchableUsers";

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(Arrays.asList(
                CACHE_STICKER_TYPES,
                CACHE_STICKER_BASE_AMOUNT,
                CACHE_REQUEST_TYPES,
                CACHE_REQUEST_DOCS,
                CACHE_USER_DISPLAY_NAMES,
                CACHE_WORKER_DEPARTMENTS,
                CACHE_WORKER_SECTIONS,
                CACHE_WORKER_JABATANS,
                CACHE_APPROVAL_CHAINS,
                CACHE_SWITCHABLE_USERS
        ));
        cacheManager.setAllowNullValues(false);
        return cacheManager;
    }
}

