create table if not exists payment (
                                       id varchar(36) not null,
                                       order_id varchar(36) not null,
                                       checkout_id varchar(36) not null,
                                       customer_id varchar(36) not null,

                                       status varchar(30) not null,

                                       total_amount decimal(19,2) not null,
                                       currency varchar(10) not null,

                                       provider varchar(30) not null,
                                       provider_payment_id varchar(100) null,

                                       failure_reason varchar(255) null,

                                       created_at timestamp not null default current_timestamp,
                                       updated_at timestamp null,

                                       primary key (id),

                                       unique key uk_payment_checkout (checkout_id),

                                       key idx_payment_order (order_id)
);

create table if not exists payment_attempt (
                                               id bigint not null auto_increment,
                                               payment_id varchar(36) not null,

                                               attempt_no int not null,
                                               status varchar(30) not null,
                                               reason varchar(255) null,

                                               created_at timestamp not null default current_timestamp,

                                               primary key (id),

                                               key idx_attempt_payment (payment_id),

                                               unique key uk_attempt_no (payment_id, attempt_no),

                                               constraint fk_attempt_payment
                                                   foreign key (payment_id) references payment(id)
                                                       on delete cascade
);

create table if not exists inbox_processed (
                                               message_id varchar(100) not null,
                                               processed_at timestamp not null default current_timestamp,
                                               primary key (message_id)
);

create index idx_payment_status on payment (status);
create index idx_payment_customer on payment (customer_id);
create index idx_payment_checkout on payment (checkout_id);