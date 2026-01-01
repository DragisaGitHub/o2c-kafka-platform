create table if not exists payment_retry_request (
    retry_request_id varchar(36) not null,
    order_id varchar(36) not null,
    created_at timestamp not null default current_timestamp,
    primary key (retry_request_id)
);

create index idx_payment_retry_order on payment_retry_request (order_id);
