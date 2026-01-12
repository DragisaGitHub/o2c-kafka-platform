create table if not exists user_mfa (
    user_id bigint not null,
    totp_enabled tinyint(1) not null default 0,
    totp_secret_enc blob null,
    enrolled_at timestamp null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp null default null on update current_timestamp,
    primary key (user_id),
    constraint fk_user_mfa_user_id foreign key (user_id) references users(id) on delete cascade
);

-- Data migration from legacy users.* MFA columns (introduced in V2)
insert into user_mfa (user_id, totp_enabled, totp_secret_enc, enrolled_at)
select
    u.id as user_id,
    u.mfa_enabled as totp_enabled,
    u.mfa_totp_secret_enc as totp_secret_enc,
    u.mfa_enrolled_at as enrolled_at
from users u
where (u.mfa_enabled = 1 or u.mfa_totp_secret_enc is not null or u.mfa_enrolled_at is not null)
on duplicate key update
    totp_enabled = values(totp_enabled),
    totp_secret_enc = values(totp_secret_enc),
    enrolled_at = values(enrolled_at);
