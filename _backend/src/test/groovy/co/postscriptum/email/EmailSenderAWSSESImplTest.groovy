package co.postscriptum.email

import co.postscriptum.RuntimeEnvironment
import co.postscriptum.internal.AwsConfig
import co.postscriptum.internal.MyConfiguration
import co.postscriptum.metrics.ComponentMetrics
import co.postscriptum.metrics.EmailDeliveryMetrics
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@ContextConfiguration(classes = TestConfiguration)
class EmailSenderAWSSESImplTest extends Specification {

    @Autowired
    private EmailSenderAWSSESImpl emailSender

    def testSendEmail() {
        given:
        def envelope = Envelope.builder()
                               .type(EnvelopeType.CONTACT_FORM)
                               .msgContent("content")
                               .title("title")
                               .recipient("john.doe@postscriptum.co")
                               .build()

        when:
        String awsMessageId = emailSender.sendEmail(envelope)

        then:
        awsMessageId.length() > 1

    }

    @Configuration
    @PropertySource(["classpath:application-dev.yml"])
    static class TestConfiguration {

        @MockBean EmailDiscWriter emailDiscWriter

        @MockBean ComponentMetrics componentMetrics

        @MockBean EmailDeliveryMetrics emailDeliveryMetrics

        @Bean
        MyConfiguration myConfiguration() {
            return new MyConfiguration()
        }

        @Bean
        EmailTemplateService envelopeCreatorService() {
            return new EmailTemplateService()
        }

        @Bean
        AwsConfig awsConfig(MyConfiguration myConfiguration) {
            return myConfiguration.createAwsConfig()
        }

        @Bean
        RuntimeEnvironment env() {
            return RuntimeEnvironment.DEV
        }

        @Bean
        EmailSenderAWSSESImpl emailSender() {
            return new EmailSenderAWSSESImpl()
        }

    }
}
