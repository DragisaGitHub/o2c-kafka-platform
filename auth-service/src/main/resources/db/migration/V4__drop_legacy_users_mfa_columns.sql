alter table users
    drop column mfa_enabled,
    drop column mfa_totp_secret_enc,
    drop column mfa_enrolled_at;
