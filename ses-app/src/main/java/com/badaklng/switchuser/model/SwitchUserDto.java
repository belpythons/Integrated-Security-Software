package com.badaklng.switchuser.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.io.Serializable;

/**
 * Lightweight, immutable projection of user data for the Switch User screen.
 * Caches essential display fields in JVM memory without exposing entity proxies,
 * password hashes, or lazy JPA relationships.
 */
@Getter
@AllArgsConstructor
public class SwitchUserDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String fullName;
    private final String email;
    private final String authType;
}

