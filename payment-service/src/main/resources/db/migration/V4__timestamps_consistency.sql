-- Ensure DB timestamps are consistent (DB is UTC) and app does not need to provide them.
-- Do NOT change MySQL server/container timezone; rely on DB defaults + ON UPDATE behavior.

-- Backfill existing rows so we can safely make updated_at NOT NULL.
update payment
set updated_at = created_at
where updated_at is null;

update payment_attempt
set updated_at = created_at
where updated_at is null;

-- payment timestamps
alter table payment
    modify column created_at timestamp not null default current_timestamp,
    modify column updated_at timestamp not null default current_timestamp on update current_timestamp;

-- payment_attempt timestamps
alter table payment_attempt
    modify column created_at timestamp not null default current_timestamp,
    modify column updated_at timestamp not null default current_timestamp on update current_timestamp;
