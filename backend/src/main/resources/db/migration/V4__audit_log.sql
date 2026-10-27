-- V4: the audit log. Who did what to the rules, and when.
--
-- Every step of the rule workflow (drafting, submitting, approving, publishing, rejecting,
-- rolling back) adds one row here, in the same transaction as the change itself. So the log
-- and the rules can never disagree: either both are saved or neither is.
--
-- Never edit this file once it has run anywhere. Add V5__... instead.

create table audit_event (
    id           bigint        generated always as identity primary key,
    occurred_at  timestamptz   not null,
    actor        varchar(64)   not null,     -- username of whoever did it ("system" for migrations)
    actor_name   varchar(128)  not null,     -- their display name at the time
    action       varchar(40)   not null,     -- what they did: one of AuditAction, e.g. RULE_SET_PUBLISHED
    target_type  varchar(32)   not null,     -- what kind of thing it was done to: RULE_SET for now
    target_id    varchar(64)   not null,     -- which one: for a rule set, its version number
    summary      varchar(300)  not null,     -- one sentence for people to read
    details      jsonb         not null      -- anything else worth keeping, e.g. {"replaced": 2}
);

-- "Everything that happened to version 3, newest first" reads straight off this index.
create index ix_audit_event_target on audit_event (target_type, target_id, id desc);

-- History is only ever added to. This trigger makes the database itself refuse to change or
-- delete a row, whoever asks: the application, a teammate in psql, or a bug. (TRUNCATE, which
-- empties a whole table, is a separate permission and is not stopped by row triggers. A demo
-- reset script may use it on purpose.)
create function refuse_change() returns trigger
language plpgsql as $$
begin
    raise exception '% is append-only: rows can be added, never changed or deleted', tg_table_name;
end;
$$;

create trigger audit_event_append_only
    before update or delete on audit_event
    for each row execute function refuse_change();

-- The record of which rule set was live when is history too.
create trigger rule_set_activation_append_only
    before update or delete on rule_set_activation
    for each row execute function refuse_change();

insert into audit_event (occurred_at, actor, actor_name, action, target_type, target_id, summary, details)
values (now(), 'system', 'Sentinel', 'RULES_MOVED_TO_DATABASE', 'RULE_SET', '2',
        'Moved rule versions 1 and 2 from Java code into the database. Version 2 is live.',
        '{"versions": [1, 2]}');
