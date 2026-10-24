-- V2: the people who use the console, and the systems that call the decision API.

create table app_user (
    id             bigint        generated always as identity primary key,
    username       varchar(64)   not null unique,
    display_name   varchar(128)  not null,
    password_hash  varchar(100)  not null,                 -- BCrypt, never the password itself
    role           varchar(16)   not null check (role in ('ADMIN', 'ANALYST')),
    created_at     timestamptz   not null default now()
);

create table api_client (
    id          bigint        generated always as identity primary key,
    name        varchar(64)   not null unique,
    key_prefix  varchar(12)   not null,                    -- first characters, to recognise a key in a list
    key_hash    varchar(64)   not null unique,             -- SHA-256 of the key, as hex
    created_at  timestamptz   not null default now(),
    revoked_at  timestamptz                                -- set when a key is withdrawn
);
