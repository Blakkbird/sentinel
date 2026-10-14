-- V1: the decision log.
--
-- One row per decision, written once and never edited. The only columns that will ever
-- be filled in later are fraud_label and labelled_at, when a chargeback arrives weeks
-- after the payment (stage 7).
--
-- Flyway runs this file once, records that it did, and never runs it again.
-- Never edit this file after it has been applied anywhere. Add V2__..., V3__... instead.

create table decision_log (
    decision_id       uuid         primary key,
    transaction_id    varchar(64)  not null,
    card_id           varchar(64)  not null,
    device_id         varchar(64),
    merchant_id       varchar(64),
    amount_minor      bigint       not null check (amount_minor > 0),
    currency          char(3)      not null,
    occurred_at       timestamptz  not null,   -- when the caller says it happened
    decided_at        timestamptz  not null,   -- our own clock; velocity uses this
    outcome           varchar(16)  not null,
    shadow_outcome    varchar(16)  not null,
    reason_codes      jsonb        not null,
    rule_set_version  integer      not null,
    degraded          boolean      not null,
    features          jsonb        not null,   -- the frozen feature snapshot
    trace             jsonb        not null,   -- what every rule did
    compute_micros    integer      not null,
    fraud_label       boolean,
    labelled_at       timestamptz,

    -- The real idempotency guarantee. Two requests with the same transaction id can
    -- never both be recorded, however they race.
    constraint uq_decision_log_transaction unique (transaction_id)
);

-- Replay walks decisions in time order over a range (stage 4).
create index ix_decision_log_decided_at on decision_log (decided_at);

-- "Show me everything this card did" during an investigation.
create index ix_decision_log_card on decision_log (card_id, decided_at desc);

-- Partial indexes: they only contain the rows matching the WHERE clause.
-- The case queue only ever looks at flagged decisions, which are a small fraction of
-- all traffic, so indexing only those keeps the index small and fast.
create index ix_decision_log_flagged on decision_log (decided_at desc)
    where outcome <> 'APPROVE';

-- The shadow report only looks at decisions a shadow rule would have changed.
create index ix_decision_log_shadow_flips on decision_log (decided_at desc)
    where outcome <> shadow_outcome;
