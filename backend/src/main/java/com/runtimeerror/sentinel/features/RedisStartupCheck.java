package com.runtimeerror.sentinel.features;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Touches Redis once, right after startup.
 *
 * Two reasons. The Redis connection is opened lazily, on first use, so without this the
 * very first real decision would pay the cost of connecting. And it prints one clear
 * line saying whether velocity features are live, which saves a lot of head scratching
 * when Docker is not running.
 *
 * It never stops the application from starting. A missing Redis means degraded mode,
 * not an outage; only a missing database is fatal (Flyway fails the startup for that).
 */
@Component
public class RedisStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisStartupCheck.class);

    private final StringRedisTemplate redis;

    public RedisStartupCheck(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            redis.opsForValue().get("sentinel:startup-check");
            log.info("Redis is reachable: velocity features are live");
        } catch (RuntimeException e) {
            log.warn("Redis is NOT reachable at startup ({}). Decisions will run in degraded mode"
                    + " until it is. Is Docker running?", e.getMessage());
        }
    }
}
