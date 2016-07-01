package co.postscriptum.security;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.io.pem.PemReader;

import javax.crypto.Cipher;
import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Slf4j
public class RSAOAEPUtils {

    private static final SecureRandom SECURE_RANDOM;

    static {
        log.info("adding BouncyCastleProvider");

        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        try {
            SECURE_RANDOM = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("can't init SecureRandom", e);
        }
    }

    public static byte[] fromPem(String pem) {
        try (PemReader pemReader = new PemReader(new StringReader(pem))) {
            return pemReader.readPemObject().getContent();
        } catch (IOException e) {
            throw new IllegalStateException("unexpected exception while reading from PEM", e);
        }
    }

    public static PublicKey toPublicKey(byte[] encoded) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC");
            return keyFactory.generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Can't generate RSA keys", e);
        }
    }

    public static PrivateKey toPrivateKey(byte[] encoded) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Can't generate RSA keys", e);
        }
    }

    public static KeyPair generateKeys() {
        try {
            final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
            keyGen.initialize(2048, SECURE_RANDOM);
            return keyGen.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Can't generate RSA keys", e);
        }
    }

    public static RSAOAEPEncrypted encrypt(byte[] plaintext, PublicKey key) {
        try {

            if (plaintext.length > 256) {
                throw new IllegalArgumentException("RSA plaintext can't exceed 256 bytes");
            }

            final Cipher cipher = Cipher.getInstance("RSA/None/OAEPWithSHA1AndMGF1Padding", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, key);

            byte[] ct = cipher.doFinal(plaintext);

            return RSAOAEPEncrypted.builder()
                                   .ct(ct)
                                   .build();

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Can't encrypt RSA plaintext", e);
        }
    }

    public static byte[] decrypt(RSAOAEPEncrypted encrypted, PrivateKey key) {
        try {
            final Cipher cipher = Cipher.getInstance("RSA/None/OAEPWithSHA1AndMGF1Padding", "BC");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(encrypted.getCt());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Can't decrypt RSA plaintext", e);
        }
    }


}
