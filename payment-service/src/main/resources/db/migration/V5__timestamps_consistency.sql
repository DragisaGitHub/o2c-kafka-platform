-- Ensure DB timestamps are consistent (DB is UTC) and app does not need to provide them.
-- Do NOT change MySQL server/container timezone; rely on DB defaults + ON UPDATE behavior.

-- payment timestamps
update payment
set updated_at = created_at
where updated_at is null;

alter table payment
    modify column created_at timestamp not null default current_timestamp,
    modify column updated_at timestamp not null default current_timestamp on update current_timestamp;

-- payment_attempt timestamps
-- V3 introduced updated_at, but older DBs might not have it.
set @payment_attempt_updated_at_exists := (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'payment_attempt'
      and column_name = 'updated_at'
);

set @payment_attempt_add_updated_at_sql := if(
    @payment_attempt_updated_at_exists = 0,
    'alter table payment_attempt add column updated_at timestamp null',
    'select 1'
);

prepare payment_attempt_stmt from @payment_attempt_add_updated_at_sql;
execute payment_attempt_stmt;
deallocate prepare payment_attempt_stmt;

update payment_attempt
set updated_at = created_at
where updated_at is null;

alter table payment_attempt
    modify column created_at timestamp not null default current_timestamp,
    modify column updated_at timestamp not null default current_timestamp on update current_timestamp;
