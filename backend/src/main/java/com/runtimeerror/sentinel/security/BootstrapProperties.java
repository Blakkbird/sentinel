package com.runtimeerror.sentinel.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * The sentinel.bootstrap section of application.yml, read into a typed object.
 * Spring fills the record from the YAML, converting "ANALYST" to Role.ANALYST and
 * display-name to displayName on the way.
 */
@ConfigurationProperties(prefix = "sentinel.bootstrap")
public record BootstrapProperties(String apiKey, List<UserSeed> users) {

    public BootstrapProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public record UserSeed(String username, String displayName, Role role, String password) {
    }
}
