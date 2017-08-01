package co.postscriptum.security

import org.apache.commons.io.IOUtils
import spock.lang.Specification

import javax.crypto.SecretKey

class AESGCMUtilsSpec extends Specification {

    def "should encrypt stream"() {
        given:
        String toEncrypt = "hello world!hello world!XYZ"
        SecretKey key = AESKeyUtils.generateRandomKey()

        when:
        InputStream ins = new ByteArrayInputStream(toEncrypt.getBytes("UTF-8"))

        ByteArrayOutputStream out = new ByteArrayOutputStream()
        byte[] iv = AESGCMUtils.encryptStream(key, ins, out, ["testabc"] as String[])

        InputStream dec =
                AESGCMUtils.decryptedStream(new ByteArrayInputStream(out.toByteArray()), key, iv, ["testabc"] as String[])

        then:
        IOUtils.toString(dec, "UTF-8") == toEncrypt
    }

    def "should encrypt and decrypt with valid password"() {
        given:
        byte[] key = AESKeyUtils.generateRandomKey().getEncoded()

        when:
        AESGCMEncryptedByPassword encrypted = AESGCMUtils.encryptByPassword("passwd", key)

        then:
        encrypted != null

        when:
        SecretKey secretKey = AESKeyUtils.toSecretKey(AESGCMUtils.decryptByPassword("passwd", encrypted))

        then:
        secretKey.getEncoded() == key
    }

    def "should encrypt and not decrypt with invalid password"() {
        given:
        byte[] key = AESKeyUtils.generateRandomKey().getEncoded()

        when:
        AESGCMEncryptedByPassword encrypted = AESGCMUtils.encryptByPassword("passwd", key)

        then:
        encrypted != null

        when:
        AESGCMUtils.decryptByPassword("passwdXX", encrypted)

        then:
        thrown(IllegalStateException)
    }

    def "should encrypt with random key"() {
        given:
        SecretKey secretKey = AESKeyUtils.generateRandomKey()

        String toEncrypt = "hello there"

        when:
        AESGCMEncrypted encrypted = AESGCMUtils.encrypt(secretKey, toEncrypt.getBytes())

        byte[] res = AESGCMUtils.decrypt(secretKey, encrypted)

        then:
        res == toEncrypt.getBytes()
    }

    def "should encrypt with AAD"() {
        given:
        SecretKey secretKey = AESKeyUtils.generateRandomKey()

        String toEncrypt = "some secret model"

        String[] aads = ["data1", "data2"]

        when:
        AESGCMEncrypted encrypted = AESGCMUtils.encrypt(secretKey, toEncrypt.getBytes(), aads)

        byte[] res = AESGCMUtils.decrypt(secretKey, encrypted, aads)

        then:
        res == toEncrypt.getBytes()
    }

}
