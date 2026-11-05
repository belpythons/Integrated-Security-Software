package com.badaklng.switchuser.service;

import com.badaklng.platform.system.service.AppUserService;
import com.badaklng.ses.config.SesCacheConfig;
import com.badaklng.switchuser.model.SwitchUserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service providing cached, high-performance lookup of active switchable users.
 * Reduces database load from 500+ entity fetches to zero on repeat visits.
 */
@Service
@RequiredArgsConstructor
public class SwitchUserService {

    private final AppUserService appUserService;

    @Transactional(readOnly = true)
    @Cacheable(value = SesCacheConfig.CACHE_SWITCHABLE_USERS)
    public List<SwitchUserDto> getActiveSwitchableUsers() {
        return appUserService.findAll().stream()
                .filter(u -> "Y".equals(u.getActiveFlag()))
                .map(u -> new SwitchUserDto(
                        u.getUsername(),
                        u.getFullName() != null && !u.getFullName().trim().isEmpty() ? u.getFullName() : u.getUsername(),
                        u.getEmail() != null ? u.getEmail() : "",
                        u.getAuthType() != null ? u.getAuthType() : "LOCAL"
                ))
                .collect(Collectors.toList());
    }

    @CacheEvict(value = SesCacheConfig.CACHE_SWITCHABLE_USERS, allEntries = true)
    public void evictSwitchableUsersCache() {
        // Cache eviction hook
    }
}

