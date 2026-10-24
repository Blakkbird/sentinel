package com.runtimeerror.sentinel.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A person who signs in to the console. One row of the app_user table (V2 migration).
 *
 * This is a JPA entity: Hibernate maps each field to a column and builds AppUser objects
 * from rows, so the repository below needs no SQL at all. That convenience is worth it for
 * a small table like this one. The decision path deliberately does not use JPA (see
 * DecisionLog for why).
 *
 * Flyway creates the table; Hibernate only reads and writes rows (ddl-auto: none in
 * application.yml). One owner for the schema means the SQL in db/migration is the truth.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // the database numbers new rows
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)                           // stored as 'ADMIN', not as a number
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;                             // filled in by the database default

    /** JPA needs a no-argument constructor to build objects from rows. Not for our own use. */
    protected AppUser() {
    }

    public AppUser(String username, String displayName, String passwordHash, Role role) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
