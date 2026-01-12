alter table users
    add column mfa_enabled tinyint(1) not null default 0,
    add column mfa_totp_secret_enc blob null,
    add column mfa_enrolled_at timestamp null;
