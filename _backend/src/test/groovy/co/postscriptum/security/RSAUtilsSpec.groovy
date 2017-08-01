package co.postscriptum.security

import spock.lang.Specification

import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

class RSAUtilsSpec extends Specification {

    def "should encrypt and decrypt with random key"() {
        given:
        String toEncrypt = "hello there"
        KeyPair keyPair = RSAOAEPUtils.generateKeys()

        when:
        PrivateKey privateKey = RSAOAEPUtils.toPrivateKey(keyPair.getPrivate().getEncoded())
        PublicKey publicKey = RSAOAEPUtils.toPublicKey(keyPair.getPublic().getEncoded())

        RSAOAEPEncrypted encrypted = RSAOAEPUtils.encrypt(toEncrypt.getBytes(), publicKey)

        then:
        RSAOAEPUtils.decrypt(encrypted, privateKey) == toEncrypt.getBytes()
    }

}
