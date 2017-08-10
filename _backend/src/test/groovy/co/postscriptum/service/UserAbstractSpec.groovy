package co.postscriptum.service

import co.postscriptum.model.bo.DataFactory
import co.postscriptum.model.bo.UserData
import co.postscriptum.model.bo.UserInternal
import co.postscriptum.security.AESGCMUtils
import co.postscriptum.security.AESKeyUtils
import co.postscriptum.security.RSAOAEPUtils
import spock.lang.Specification

import javax.crypto.SecretKey
import java.security.KeyPair

abstract class UserAbstractSpec extends Specification {

    UserData userData

    SecretKey encryptionKey

    def setup() {
        KeyPair adminKeyPair = RSAOAEPUtils.generateKeys()

        String loginPassword = UUID.randomUUID().toString()

        UserInternal internal = DataFactory.newUserInternal(loginPassword, adminKeyPair.getPublic())

        encryptionKey = AESKeyUtils.toSecretKey(AESGCMUtils.decryptByPassword(loginPassword, internal.getEncryptionKey()))

        userData = DataFactory.newUserData(DataFactory.newUser(), internal)

        userData.getInternal().setQuotaBytes(1024 * 1024 * 50)
    }

}
