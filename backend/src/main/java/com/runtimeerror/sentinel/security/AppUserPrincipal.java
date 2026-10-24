package com.runtimeerror.sentinel.security;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * A signed-in console user, in the shape Spring Security understands (UserDetails).
 *
 * It lives in the HTTP session for as long as the person stays signed in, which is why it
 * holds copies of the few fields we need rather than the JPA entity itself.
 *
 * Roles become "authorities" with a ROLE_ prefix. An admin also gets ROLE_ANALYST, so any
 * rule written as hasRole("ANALYST") lets admins through as well.
 */
public final class AppUserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String displayName;
    private final Role role;
    private String passwordHash;

    private AppUserPrincipal(String username, String displayName, Role role, String passwordHash) {
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.passwordHash = passwordHash;
    }

    public static AppUserPrincipal from(AppUser user) {
        return new AppUserPrincipal(user.getUsername(), user.getDisplayName(), user.getRole(), user.getPasswordHash());
    }

    public String displayName() {
        return displayName;
    }

    public Role role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == Role.ADMIN) {
            return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_ANALYST"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /** Spring calls this once sign-in has succeeded, so the hash never sits in the session. */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
