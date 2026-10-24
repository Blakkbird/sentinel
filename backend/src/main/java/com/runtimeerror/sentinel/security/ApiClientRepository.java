package com.runtimeerror.sentinel.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiClientRepository extends JpaRepository<ApiClient, Long> {

    /** Every key that has not been withdrawn. */
    List<ApiClient> findByRevokedAtIsNull();

    boolean existsByKeyHash(String keyHash);
}
