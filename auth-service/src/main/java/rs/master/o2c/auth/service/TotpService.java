package rs.master.o2c.auth.service;

import java.time.Instant;

public interface TotpService {

    String toBase32NoPadding(byte[] secret);

    boolean isValidCode(byte[] secret, String code);

    boolean isValidCode(byte[] secret, String code, Instant now);

    String currentCode(byte[] secret);

    String buildOtpAuthUri(String issuer, String username, byte[] secret);
}
