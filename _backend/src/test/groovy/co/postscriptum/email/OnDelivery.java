package co.postscriptum.email;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
public class OnDelivery implements EmailDelivery.OnDelivery {

    private List<String> receivedMessagesId = new ArrayList<>();

    @Override
    public void onDelivery(String messageId,
                           String envelopeId,
                           Map<String, String> headers,
                           DeliveryType deliveryType,
                           Map<String, Object> bounceCause) {

        log.info("onDelivery callback: messageId: {}, envelopeId: {}, headers: {}, deliveryType: {}, bounceCause: {}",
                 messageId, envelopeId, headers, deliveryType, bounceCause);

        receivedMessagesId.add(messageId);
    }

}
