package co.postscriptum.email;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Profile("dev")
@Component
public class EmailDeliveryDummyImpl implements EmailDeliveryDummy {

    private final List<DeliveredEmail> deliveredEmails = new CopyOnWriteArrayList<>();

    private int delayTimeMs = 3000;

    @Override
    public void process(OnDelivery onDelivery) {

        log.info("Checking for delivered emails");

        List<DeliveredEmail> toRemove = deliveredEmails
                .stream()
                .filter(deliveredEmail -> System.currentTimeMillis() - deliveredEmail.time > delayTimeMs)
                .peek(deliveredEmail -> {
                    onDelivery.onDelivery(
                            deliveredEmail.messageId,
                            deliveredEmail.envelopeId,
                            deliveredEmail.headers,
                            DeliveryType.Delivery,
                            null);

                })
                .collect(Collectors.toList());

        deliveredEmails.removeAll(toRemove);

    }

    @Override
    public void markAsDelivered(Envelope envelope, String messageId) {

        log.info("Enqueuing messageId: {} as delivered in {} ms", messageId, delayTimeMs);

        deliveredEmails.add(DeliveredEmail.builder()
                                          .envelopeId(envelope.getEnvelopeId())
                                          .headers(envelope.getHeaders())
                                          .messageId(messageId)
                                          .build());
    }

    public void setDelayTimeMs(int delayTimeMs) {
        this.delayTimeMs = delayTimeMs;
    }

    @Value
    @Builder
    private static class DeliveredEmail {

        long time = System.currentTimeMillis();
        String envelopeId;
        String messageId;
        Map<String, String> headers;

    }

}
