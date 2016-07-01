package co.postscriptum.security;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class AESKeyUtils {

    private static final SecureRandom SECURE_RANDOM;

    static {
        try {
            SECURE_RANDOM = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("can't init SecureRandom", e);
        }
    }

    public static byte[] randomByteArray(int size) {
        byte[] salt = new byte[size];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    public static byte[] randomSalt() {
        return randomByteArray(8);
    }

    public static SecretKey toSecretKey(byte[] data) {
        if (data.length != 32) {
            throw new IllegalArgumentException("AES key length=" + data.length + " is invalid");
        }
        return new SecretKeySpec(data, "AES");
    }

    public static SecretKey generateRandomKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Can't generate random AES key", e);
        }
    }

    public static SecretKey deriveKey(String password, byte[] salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            SecretKey tmp = factory.generateSecret(new PBEKeySpec(password.toCharArray(), salt, 65536, 256));
            return toSecretKey(tmp.getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Can't derive AES key from password and salt", e);
        }
    }

}
