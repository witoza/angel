package co.postscriptum.metrics;

import co.postscriptum.email.DeliveryType;
import co.postscriptum.email.EnvelopeType;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Synchronized;
import lombok.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EmailDeliveryMetrics {

    @Getter
    private EmailDeliveryMetricsData stats = new EmailDeliveryMetricsData();

    @Synchronized
    public void process(String messageId, String envelopeId, DeliveryType deliveryType, Map<String, Object> bounceCause) {

        Map<EnvelopeType, Integer> map;

        if (deliveryType == DeliveryType.Delivery) {
            stats.totalDelivered++;
            map = stats.deliveredByType;
        } else if (deliveryType == DeliveryType.Bounce) {
            stats.totalBounced++;
            map = stats.bouncedByType;

            stats.last10Bounced.add(EmailDeliveryMetricsData.Bounced.builder()
                                                                    .envelopeId(envelopeId)
                                                                    .cause(bounceCause)
                                                                    .build());
            if (stats.last10Bounced.size() > 10) {
                stats.last10Bounced.remove(0);
            }

        } else {
            throw new IllegalStateException("can't process deliveryType: " + deliveryType);
        }

        EnvelopeType envelopeType = EnvelopeType.fromEnvelopeId(envelopeId);
        map.putIfAbsent(envelopeType, 0);
        map.computeIfPresent(envelopeType, (k, v) -> v + 1);

    }

    @Data
    static class EmailDeliveryMetricsData {

        Integer totalBounced = 0;

        Map<EnvelopeType, Integer> bouncedByType = new LinkedHashMap<>();

        List<Bounced> last10Bounced = new ArrayList<>();

        Integer totalDelivered = 0;
        Map<EnvelopeType, Integer> deliveredByType = new LinkedHashMap<>();

        @Value
        @Builder
        static class Bounced {
            long time = System.currentTimeMillis();
            String envelopeId;
            Map<String, Object> cause;
        }

    }

}
