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
class EmailDeliveryAWSSQSImplTest extends Specification {

    @Autowired
    private EmailSender emailSender

    @Autowired
    private EmailDelivery emailDelivery

    def testSendAndDeliver() {

        given:
        Map<String, String> headers = new HashMap<>()
        headers.put("userUuid", "user-john+model@postscriptum.co")
        EnvelopeType envelopeType = EnvelopeType.RELEASE_ITEM
        String content = "<h1>con</h1><b>ten</b>t"
        String title = "title"

        when:
        String awsMessageId = emailSender.sendEmail(Envelope.builder()
                                                            .type(envelopeType)
                                                            .headers(headers)
                                                            .recipient("john.doe@postscriptum.co")
                                                            .title(title)
                                                            .msgContent(content)
                                                            .build())

        Thread.sleep(3000)

        OnDelivery onDelivery = new OnDelivery()

        emailDelivery.process(onDelivery)

        then:
        onDelivery.getReceivedMessagesId().contains(awsMessageId)

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
        AwsConfig awsConfig(MyConfiguration myConfiguration) {
            return myConfiguration.createAwsConfig()
        }

        @Bean
        EmailTemplateService envelopeCreatorService() {
            return new EmailTemplateService()
        }

        @Bean
        RuntimeEnvironment env() {
            return RuntimeEnvironment.CERT
        }

        @Bean
        EmailSenderAWSSESImpl emailSender() {
            return new EmailSenderAWSSESImpl()
        }

        @Bean
        EmailDelivery emailDelivery() {
            return new EmailDeliveryAWSSQSImpl()
        }

    }
}
