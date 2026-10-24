package com.runtimeerror.sentinel.security;

/** The signed-in user, as the console sees it. Never includes the password hash. */
public record CurrentUser(String username, String displayName, Role role) {

    static CurrentUser of(AppUserPrincipal user) {
        return new CurrentUser(user.getUsername(), user.displayName(), user.role());
    }
}
