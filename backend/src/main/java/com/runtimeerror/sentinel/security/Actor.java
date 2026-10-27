package com.runtimeerror.sentinel.security;

import org.springframework.security.core.Authentication;

/**
 * Who is doing something, as the rule workflow sees it: a username for the record, a name
 * for people to read, and whether they are an admin.
 *
 * Services take an Actor rather than Spring Security's Authentication, so the rules about who
 * may do what can be read (and tested) without any of Spring Security's machinery.
 */
public record Actor(String username, String displayName, boolean admin) {

    public static Actor from(Authentication authentication) {
        String username = authentication.getName();
        // Console accounts carry a display name. Anything else (a test user, for example)
        // falls back to the username.
        String displayName = authentication.getPrincipal() instanceof AppUserPrincipal user
                ? user.displayName()
                : username;
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return new Actor(username, displayName, admin);
    }
}
