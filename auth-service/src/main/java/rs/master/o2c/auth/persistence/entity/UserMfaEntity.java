package rs.master.o2c.auth.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("user_mfa")
public class UserMfaEntity {

    @Id
    @Column("user_id")
    private Long userId;

    @Column("totp_enabled")
    private boolean totpEnabled;

    @Column("totp_secret_enc")
    private byte[] totpSecretEnc;

    @Column("enrolled_at")
    private Instant enrolledAt;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    public UserMfaEntity() {
    }

    public UserMfaEntity(Long userId, boolean totpEnabled, byte[] totpSecretEnc, Instant enrolledAt, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.totpEnabled = totpEnabled;
        this.totpSecretEnc = totpSecretEnc;
        this.enrolledAt = enrolledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long userId() { return userId; }
    public boolean totpEnabled() { return totpEnabled; }
    public byte[] totpSecretEnc() { return totpSecretEnc; }
    public Instant enrolledAt() { return enrolledAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setTotpEnabled(boolean totpEnabled) { this.totpEnabled = totpEnabled; }
    public void setTotpSecretEnc(byte[] totpSecretEnc) { this.totpSecretEnc = totpSecretEnc; }
    public void setEnrolledAt(Instant enrolledAt) { this.enrolledAt = enrolledAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
