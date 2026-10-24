package com.runtimeerror.sentinel.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * How Spring Security finds a user at sign-in. It calls loadUserByUsername, then checks the
 * typed password against the stored BCrypt hash itself.
 *
 * Spring reports an unknown username exactly like a wrong password ("bad credentials"), so
 * the sign-in form never reveals which usernames exist.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByUsername(username)
                .map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("no user called " + username));
    }
}
