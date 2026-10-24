package com.runtimeerror.sentinel.security;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/auth/me tells the console who is signed in.
 *
 * Signing in and out have no controller here: Spring Security handles
 * POST /api/auth/login and POST /api/auth/logout itself (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public CurrentUser me(@AuthenticationPrincipal AppUserPrincipal user) {
        if (user == null) {
            // Signed in some other way (for example a test user), not with a console account.
            throw new InsufficientAuthenticationException("Sign in with a console account.");
        }
        return CurrentUser.of(user);
    }
}
