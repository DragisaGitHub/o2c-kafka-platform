alter table outbox_event
    add column locked_at timestamp null,
    add column locked_by varchar(100) null;

create index idx_outbox_lock
    on outbox_event (published_at, locked_at, created_at);