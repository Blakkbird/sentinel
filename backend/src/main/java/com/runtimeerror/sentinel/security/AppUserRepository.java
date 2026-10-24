package com.runtimeerror.sentinel.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Database access for AppUser. There is no implementation class anywhere: Spring Data
 * writes one at startup, deriving each query from the method name.
 *
 *   findByUsername    ->  select ... from app_user where username = ?
 *   existsByUsername  ->  select count(*) > 0 from app_user where username = ?
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);
}
