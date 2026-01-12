package rs.master.o2c.auth.service;

public interface TotpCryptoService {

    byte[] encrypt(byte[] plaintext);

    byte[] decrypt(byte[] ivAndCiphertext);
}
