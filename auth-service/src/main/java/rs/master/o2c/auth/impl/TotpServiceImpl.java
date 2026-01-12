package rs.master.o2c.auth.impl;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;
import rs.master.o2c.auth.service.TotpService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;

@Service
public class TotpServiceImpl implements TotpService {

    private static final Base32 BASE32 = new Base32();

    @Override
    public String toBase32NoPadding(byte[] secret) {
        if (secret == null) {
            throw new IllegalArgumentException("secret is required");
        }
        String b32 = BASE32.encodeToString(secret);
        return b32.replace("=", "").trim();
    }

    @Override
    public boolean isValidCode(byte[] secret, String code) {
        return isValidCode(secret, code, Instant.now());
    }

    @Override
    public boolean isValidCode(byte[] secret, String code, Instant now) {
        if (secret == null || secret.length == 0) {
            return false;
        }
        String normalized = code == null ? "" : code.trim();
        if (!normalized.matches("\\d{6}")) {
            return false;
        }

        long step = (now.getEpochSecond() / 30L);
        for (long skew = -1; skew <= 1; skew++) {
            String expected = generateCode(secret, step + skew);
            if (constantTimeEquals(expected, normalized)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String currentCode(byte[] secret) {
        long step = Instant.now().getEpochSecond() / 30L;
        return generateCode(secret, step);
    }

    @Override
    public String buildOtpAuthUri(String issuer, String username, byte[] secret) {
        String iss = issuer == null ? "" : issuer.trim();
        String user = username == null ? "" : username.trim();
        if (iss.isBlank() || user.isBlank()) {
            throw new IllegalArgumentException("issuer and username are required");
        }

        String label = iss + ":" + user;
        String secretB32 = toBase32NoPadding(secret);

        // label encoding: keep simple (demo) + avoid introducing a URI encoder dependency.
        String labelEscaped = label.replace(" ", "%20");
        String issuerEscaped = iss.replace(" ", "%20");

        return "otpauth://totp/" + labelEscaped
                + "?secret=" + secretB32
                + "&issuer=" + issuerEscaped
                + "&digits=6&period=30&algorithm=SHA1";
    }

    private static String generateCode(byte[] secret, long step) {
        byte[] msg = ByteBuffer.allocate(8).putLong(step).array();
        byte[] hash = hmacSha1(secret, msg);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);

        int mod = (int) Math.pow(10, 6);
        int otp = binary % mod;
        return String.format("%0" + 6 + "d", otp);
    }

    private static byte[] hmacSha1(byte[] key, byte[] msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(msg);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] x = a.getBytes(StandardCharsets.US_ASCII);
        byte[] y = b.getBytes(StandardCharsets.US_ASCII);
        if (x.length != y.length) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < x.length; i++) {
            r |= x[i] ^ y[i];
        }
        return r == 0;
    }
}
