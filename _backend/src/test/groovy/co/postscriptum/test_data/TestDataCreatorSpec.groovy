package co.postscriptum.test_data

import co.postscriptum.fs.FS
import co.postscriptum.fs.HDFS
import co.postscriptum.internal.Utils
import co.postscriptum.security.RSAOAEPUtils
import co.postscriptum.service.FileService
import co.postscriptum.service.MessageService
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

            String json = Utils.toJson(testUser.createUserData(adminKeyPair.getPublic()))

            println json

            assert json.length() > 100
        }

        then:
        true
    }

    @Configuration
    @PropertySource(["classpath:application-test.properties"])
    static class TestConfiguration {

        @Bean
        FS fs() {
            return new HDFS()
        }

        @Bean
        MessageService messageService() {
            return new MessageService()
        }


        @Bean
        FileService fileService() {
            return new FileService(fs())
        }

        @Bean
        TestDataCreator testDataCreator() {
            return new TestDataCreator()
        }

    }

}
