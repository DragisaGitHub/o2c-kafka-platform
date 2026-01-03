alter table payment_attempt
    add column provider_payment_id varchar(36) null;

alter table payment_attempt
    add column updated_at timestamp null;

create unique index uk_attempt_provider_payment_id on payment_attempt (provider_payment_id);
