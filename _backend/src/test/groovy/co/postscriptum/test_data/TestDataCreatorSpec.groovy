package co.postscriptum.test_data

import co.postscriptum.fs.FS
import co.postscriptum.fs.HDFS
import co.postscriptum.internal.FileEncryptionService
import co.postscriptum.internal.Utils
import co.postscriptum.model.bo.User
import co.postscriptum.security.RSAOAEPUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

import java.security.KeyPair

@ContextConfiguration(classes = TestConfiguration)
class TestDataCreatorSpec extends Specification {

    @Autowired
    private TestDataCreator testDataCreator

    def "should create all test users"() {
        given:
        KeyPair adminKeyPair = RSAOAEPUtils.generateKeys()

        when:
        for (TestUser testUser : testDataCreator.getTestUsers()) {

            User user = testUser.createUser()

            String json = Utils.toJson(testUser.createUserData(user, adminKeyPair.getPublic()))

            println json

            assert json.length() > 1
        }

        then:
        true
    }

    @Configuration
    @PropertySource(["classpath:application-test.properties"])
    static class TestConfiguration {

        @Bean
        FileEncryptionService uploadsEncryptionService() {
            return new FileEncryptionService()
        }

        @Bean
        FS fs() {
            return new HDFS()
        }

        @Bean
        TestDataCreator testDataCreator() {
            return new TestDataCreator()
        }

    }

}
