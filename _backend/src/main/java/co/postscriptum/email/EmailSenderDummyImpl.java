package co.postscriptum.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Profile("dev")
@Component
public class EmailSenderDummyImpl implements EmailSender {

    @Autowired
    private EmailDeliveryDummy emailDeliveryDummy;

    @Override
    public String sendEmail(Envelope envelope) {
        log.info("sending email: envelope={}", envelope);

        String messageId = UUID.randomUUID().toString();

        emailDeliveryDummy.markAsDelivered(envelope, messageId);

        return messageId;

    }

}
