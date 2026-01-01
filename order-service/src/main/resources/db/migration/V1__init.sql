create table if not exists orders (
                                      id varchar(36) not null,
    customer_id varchar(36) not null,
    status varchar(30) not null,
    total_amount decimal(19,2) not null,
    currency varchar(10) not null,
    created_at timestamp not null default current_timestamp,
    primary key (id)
    );

create table if not exists outbox_event (
                                            id char(36) not null,
    aggregate_type varchar(50) not null,
    aggregate_id varchar(36) not null,
    event_type varchar(100) not null,
    payload json not null,
    created_at timestamp not null default current_timestamp,
    published_at timestamp null,
    primary key (id)
    );

create table if not exists inbox_processed (
                                               message_id varchar(100) not null,
    processed_at timestamp not null default current_timestamp,
    primary key (message_id)
    );

create index idx_outbox_unpublished_created
    on outbox_event (published_at, created_at);