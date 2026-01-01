create table if not exists checkout (
                                        id varchar(36) not null,
                                        order_id varchar(36) not null,
                                        customer_id varchar(36) not null,
                                        status varchar(30) not null,
                                        total_amount decimal(19,2) not null,
                                        currency varchar(10) not null,
                                        created_at timestamp not null default current_timestamp,
                                        primary key (id),
                                        unique key uk_checkout_order (order_id)
);

create table if not exists inbox_processed (
                                               message_id varchar(100) not null,
                                               processed_at timestamp not null default current_timestamp,
                                               primary key (message_id)
);

create index idx_checkout_status on checkout (status);
create index idx_checkout_customer on checkout (customer_id);