package co.postscriptum.email;

import co.postscriptum.internal.Utils;
import co.postscriptum.metrics.ComponentMetrics;
import co.postscriptum.web.AuthHelper;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Component
@Slf4j
public class EmailDiscWriter {

    @Value("${my.emails_dir:.emails/}")
    private String emailsDir;

    @Autowired
    private ComponentMetrics componentMetrics;

    private void persist(Object data, Path path) {
        try {
            FileUtils.writeStringToFile(path.toFile(), Utils.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("can't write envelope to disk", e);
            componentMetrics.put(this.getClass(), "persist", 0, e);
        }
    }

    public void envelopeEnqueued(Envelope envelope) {
        EnvelopeEnqueuedInfo data = EnvelopeEnqueuedInfo.builder()
                                                        .envelope(envelope)
                                                        .reqId(MDC.get("reqId"))
                                                        .principal(AuthHelper.getLoggedUsername()
                                                                             .orElse("not logged"))
                                                        .build();

        Path file = Paths.get(emailsDir + "/" + envelope.getEnvelopeId());

        log.info("persisting {}: envelopeId={} to {}", data.getClass().getSimpleName(), envelope.getEnvelopeId(), file);
        //TODO: encrypt content

        persist(data, file);
    }

    public void envelopeSent(Envelope envelope, String messageId) {
        EnvelopeSentInfo data = EnvelopeSentInfo.builder()
                                                .messageId(messageId)
                                                .build();

        Path file = Paths.get(emailsDir + "/" + envelope.getEnvelopeId() + ".sent");

        log.info("persisting {}: messageId={}, envelopeId={} to {}",
                 data.getClass().getSimpleName(),
                 messageId,
                 envelope.getEnvelopeId(),
                 file);

        persist(data, file);
    }

    public void emailDelivered(String messageId,
                               String envelopeId,
                               DeliveryType deliveryType,
                               Map<String, Object> bounceCause) {

        EmailDeliveredInfo data = EmailDeliveredInfo.builder()
                                                    .deliveryType(deliveryType)
                                                    .bounceCause(bounceCause)
                                                    .build();

        Path file = Paths.get(emailsDir + "/" + envelopeId + "." + deliveryType.toString().toLowerCase());

        log.info("persisting {}: messageId={}, envelopeId={} to {}",
                 data.getClass().getSimpleName(),
                 messageId,
                 envelopeId,
                 file);

        persist(data, file);

    }

    @lombok.Value
    @Builder
    private static class EnvelopeEnqueuedInfo {

        private long creationTime = System.currentTimeMillis();
        private String reqId;
        private String principal;
        private Envelope envelope;
    }

    @lombok.Value
    @Builder
    private static class EnvelopeSentInfo {

        private long creationTime = System.currentTimeMillis();
        private String messageId;
    }

    @lombok.Value
    @Builder
    private static class EmailDeliveredInfo {

        private long creationTime = System.currentTimeMillis();
        private DeliveryType deliveryType;
        private Map<String, Object> bounceCause;
    }
}
