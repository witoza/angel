package co.postscriptum.email

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@ContextConfiguration(classes = TestConfiguration)
class DeliveryServiceDummyImplSpec extends Specification {

    @Autowired
    private EmailSender emailSender

    @Autowired
    private EmailDeliveryDummyImpl emailDelivery

    def smokeTest() {
        when:
        emailDelivery.setDelayTimeMs(100)

        Map<String, String> headers = new HashMap<>()
        headers.put("userUuid", "user-john+model@postscriptum.co")

        Envelope envelope = Envelope.builder()
                                    .type(EnvelopeType.RELEASE_ITEM)
                                    .headers(headers)
                                    .recipient("witold.zegarowski@gmail.com")
                                    .title("title")
                                    .msgContent("<h1>con</h1><b>ten</b>t")
                                    .build()

        String message1Id = emailSender.sendEmail(envelope)
        String message2Id = emailSender.sendEmail(envelope)

        OnDelivery onDelivery = new OnDelivery()

        emailDelivery.process(onDelivery)

        then:
        onDelivery.getReceivedMessagesId().isEmpty()

        when:
        Thread.sleep(110)
        emailDelivery.process(onDelivery)

        then:
        onDelivery.getReceivedMessagesId() == [message1Id, message2Id]
    }

    @Configuration
    @PropertySource(["classpath:application-dev.yml"])
    static class TestConfiguration {

        @Bean
        EmailDeliveryDummyImpl deliveryService() {
            return new EmailDeliveryDummyImpl()
        }

        @Bean
        EmailSender emailSender() {
            return new EmailSenderDummyImpl()
        }

    }

}
