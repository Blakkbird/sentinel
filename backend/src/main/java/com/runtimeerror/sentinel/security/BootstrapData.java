package com.runtimeerror.sentinel.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the demo accounts and the API key on first start, so a fresh database (a new
 * Neon branch, a teammate's laptop, the CI test database) is usable straight away.
 *
 * It only ever adds what is missing. It never changes an existing user's password, so a
 * password changed later is not reset by the next restart.
 */
@Component
public class BootstrapData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapData.class);

    private final BootstrapProperties properties;
    private final AppUserRepository users;
    private final ApiClientRepository clients;
    private final ApiKeyRegistry apiKeys;
    private final PasswordEncoder passwords;

    public BootstrapData(BootstrapProperties properties, AppUserRepository users, ApiClientRepository clients,
                         ApiKeyRegistry apiKeys, PasswordEncoder passwords) {
        this.properties = properties;
        this.users = users;
        this.clients = clients;
        this.apiKeys = apiKeys;
        this.passwords = passwords;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (BootstrapProperties.UserSeed seed : properties.users()) {
            if (seed.password() == null || seed.password().isBlank() || users.existsByUsername(seed.username())) {
                continue;
            }
            users.save(new AppUser(seed.username(), seed.displayName(), passwords.encode(seed.password()), seed.role()));
            log.info("Created console user '{}' ({})", seed.username(), seed.role());
        }

        String key = properties.apiKey();
        if (key != null && !key.isBlank()) {
            String hash = ApiKeys.hash(key);
            if (!clients.existsByKeyHash(hash)) {
                clients.save(new ApiClient("bootstrap-" + hash.substring(0, 8), ApiKeys.prefix(key), hash));
                log.info("Registered the bootstrap API key ({}...)", ApiKeys.prefix(key));
            }
        }

        apiKeys.reload();
    }
}
