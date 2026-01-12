package rs.master.o2c.auth.impl;

import org.springframework.stereotype.Service;
import rs.master.o2c.auth.config.TotpProperties;
import rs.master.o2c.auth.service.TotpCryptoService;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

@Service
public class TotpCryptoServiceImpl implements TotpCryptoService {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKey key;

    public TotpCryptoServiceImpl(TotpProperties props) {
        this.key = new SecretKeySpec(sha256(props.encryptionKey()), "AES");
    }

    @Override
    public byte[] encrypt(byte[] plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext is required");
        }

        byte[] iv = new byte[IV_LEN];
        random.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] out = new byte[IV_LEN + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ciphertext, 0, out, IV_LEN, ciphertext.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] ivAndCiphertext) {
        if (ivAndCiphertext == null || ivAndCiphertext.length <= IV_LEN) {
            throw new IllegalArgumentException("ciphertext is invalid");
        }

        byte[] iv = Arrays.copyOfRange(ivAndCiphertext, 0, IV_LEN);
        byte[] ciphertext = Arrays.copyOfRange(ivAndCiphertext, IV_LEN, ivAndCiphertext.length);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt", e);
        }
    }

    private static byte[] sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
