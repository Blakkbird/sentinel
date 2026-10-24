package com.runtimeerror.sentinel.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Small helpers for API keys.
 *
 * We store a SHA-256 hash of each key, never the key. If the database ever leaked, the
 * hashes would be useless to an attacker: you cannot turn a hash back into the key.
 *
 * Why SHA-256 here, when passwords get BCrypt? BCrypt is deliberately slow, to stop
 * someone guessing short human passwords by trying millions of them. A generated API key
 * is long and random, so guessing is hopeless anyway; a fast hash is safe, and being fast
 * matters because a key is checked on every single payment. Being deterministic (the same
 * key always gives the same hash) is also what lets us look a key up by its hash.
 */
public final class ApiKeys {

    /** The HTTP header a caller puts its key in. */
    public static final String HEADER = "X-Api-Key";

    private ApiKeys() {
    }

    /** SHA-256 of the key, as 64 lowercase hex characters. */
    public static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java runtime ships SHA-256", e);
        }
    }

    /** The first few characters: enough to recognise a key in a list, useless for calling the API. */
    public static String prefix(String key) {
        return key.length() > 8 ? key.substring(0, 8) : key.substring(0, Math.min(4, key.length()));
    }
}
