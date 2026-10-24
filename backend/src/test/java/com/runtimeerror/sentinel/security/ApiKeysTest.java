package com.runtimeerror.sentinel.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeysTest {

    @Test
    void hashesWithSha256AsLowercaseHex() {
        // The published SHA-256 test vector for "abc" (FIPS 180-2).
        assertThat(ApiKeys.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void theSameKeyAlwaysGivesTheSameHashAndDifferentKeysDifferentOnes() {
        assertThat(ApiKeys.hash("key-one")).isEqualTo(ApiKeys.hash("key-one"));
        assertThat(ApiKeys.hash("key-one")).isNotEqualTo(ApiKeys.hash("key-two"));
    }

    @Test
    void showsOnlyTheStartOfAKey() {
        assertThat(ApiKeys.prefix("sk_live_0123456789")).isEqualTo("sk_live_");
        assertThat(ApiKeys.prefix("short")).isEqualTo("shor");
    }
}
