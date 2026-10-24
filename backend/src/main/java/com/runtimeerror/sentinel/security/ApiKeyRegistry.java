package com.runtimeerror.sentinel.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The active API keys, held in memory.
 *
 * Checking a key must not cost a database round trip: it happens on every payment, and on
 * Neon each round trip crosses the internet. So the hashes are loaded once, at startup, and
 * every check is a map lookup.
 *
 * Nothing re-reads the table on a timer either. A periodic query would keep Neon awake all
 * day and spend the free plan's monthly hours. When keys can be created or withdrawn from
 * the console, that code will call reload() itself.
 */
@Component
public class ApiKeyRegistry {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyRegistry.class);

    private final ApiClientRepository clients;

    // volatile: a reload swaps in a whole new map, and every thread sees the new one at once.
    private volatile Map<String, ApiClientPrincipal> activeByHash = Map.of();

    public ApiKeyRegistry(ApiClientRepository clients) {
        this.clients = clients;
    }

    public void reload() {
        Map<String, ApiClientPrincipal> fresh = new HashMap<>();
        for (ApiClient client : clients.findByRevokedAtIsNull()) {
            fresh.put(client.getKeyHash(), new ApiClientPrincipal(client.getName(), client.getKeyPrefix()));
        }
        activeByHash = Map.copyOf(fresh);
        log.info("API keys loaded: {} active", fresh.size());
    }

    /** The client this key belongs to, or empty if the key is unknown or withdrawn. */
    public Optional<ApiClientPrincipal> find(String presentedKey) {
        return Optional.ofNullable(activeByHash.get(ApiKeys.hash(presentedKey)));
    }
}
