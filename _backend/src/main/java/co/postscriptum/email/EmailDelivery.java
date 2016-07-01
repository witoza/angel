package co.postscriptum.email;

import java.util.Map;

public interface EmailDelivery {

    void process(OnDelivery onDelivery);

    interface OnDelivery {
        void onDelivery(String messageId,
                        String envelopeId,
                        Map<String, String> headers,
                        DeliveryType deliveryType,
                        Map<String, Object> bounceCause);
    }

}
