package co.postscriptum.security;

import co.postscriptum.model.bo.PasswordEncryption;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;

@Slf4j
public class AESGCMUtils {

    static {
        log.info("Registering BouncyCastleProvider");
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    private static Cipher getAesGcmCipher() throws
            NoSuchAlgorithmException,
            NoSuchProviderException,
            NoSuchPaddingException {
        return Cipher.getInstance("AES/GCM/NoPadding", "BC");
    }

    private static void updateAdd(Cipher cipher, String[] aads) {
        for (String aad : aads) {
            cipher.updateAAD(aad.getBytes());
        }
    }

    public static AESGCMEncryptedByPassword encryptByPassword(String password, byte[] plaintext, String... aads) {
        byte[] salt = AESKeyUtils.randomSalt();
        SecretKey key = AESKeyUtils.deriveKey(password, salt);

        return AESGCMEncryptedByPassword.builder()
                                        .passwordSalt(salt)
                                        .encrypted(encrypt(key, plaintext, aads))
                                        .build();
    }

    public static AESGCMEncrypted encrypt(SecretKey key, byte[] plaintext, String... aads) {
        try {

            Cipher cipher = getAesGcmCipher();
            cipher.init(Cipher.ENCRYPT_MODE, key);

            AlgorithmParameters params = cipher.getParameters();
            byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();

            updateAdd(cipher, aads);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CipherOutputStream cos = new CipherOutputStream(baos, cipher);
            cos.write(plaintext);
            cos.close();

            return AESGCMEncrypted.builder()
                                  .ct(baos.toByteArray())
                                  .iv(iv)
                                  .build();

        } catch (Exception e) {
            throw new IllegalStateException("Can't encrypt plaintext by AES-GCM", e);
        }
    }

    public static byte[] decryptByPassword(String password, PasswordEncryption passwordEncryption, byte[] ct) {
        SecretKey key = AESKeyUtils.deriveKey(password, passwordEncryption.getSalt());
        return decrypt(key, ct, passwordEncryption.getIv());
    }

    public static byte[] decryptByPassword(String password, AESGCMEncryptedByPassword encryptedByPassword) {
        SecretKey key = AESKeyUtils.deriveKey(password, encryptedByPassword.getPasswordSalt());
        return decrypt(key, encryptedByPassword.getEncrypted());
    }

    public static byte[] decrypt(SecretKey key, AESGCMEncrypted encrypted, String... aads) {
        return decrypt(key, encrypted.getCt(), encrypted.getIv(), aads);
    }

    private static byte[] decrypt(SecretKey key, byte[] ct, byte[] iv, String... aads) {
        try {

            Cipher cipher = getAesGcmCipher();
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

            updateAdd(cipher, aads);

            CipherInputStream cis = new CipherInputStream(new ByteArrayInputStream(ct), cipher);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            IOUtils.copy(cis, baos);

            return baos.toByteArray();

        } catch (Exception e) {
            throw new IllegalStateException("Can't decrypt AES-GCM ciphertext", e);
        }

    }

    public static byte[] encryptStream(SecretKey key, InputStream input, OutputStream output, String[] aads) {

        try {

            Cipher cipher = getAesGcmCipher();
            cipher.init(Cipher.ENCRYPT_MODE, key);

            updateAdd(cipher, aads);

            AlgorithmParameters params = cipher.getParameters();
            byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();

            try (CipherOutputStream cos = new CipherOutputStream(output, cipher)) {
                IOUtils.copy(input, cos);
            }

            return iv;

        } catch (Exception e) {
            throw new IllegalStateException("Can't encrypt stream by AES-GCM", e);
        }

    }

    public static CipherInputStream decryptedStream(InputStream fis, SecretKey key, byte[] iv, String[] aads) {
        try {

            Cipher cipher = getAesGcmCipher();
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

            updateAdd(cipher, aads);

            return new CipherInputStream(fis, cipher);

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Can't decrypt AES-GCM cipherstream", e);
        }
    }

}
