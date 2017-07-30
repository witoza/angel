package co.postscriptum.job;

import co.postscriptum.db.DB;
import co.postscriptum.email.DeliveryType;
import co.postscriptum.email.EmailDelivery;
import co.postscriptum.email.EmailDiscWriter;
import co.postscriptum.email.EmailSender;
import co.postscriptum.email.Envelope;
import co.postscriptum.email.EnvelopeType;
import co.postscriptum.metrics.EmailDeliveryMetrics;
import co.postscriptum.service.UserDataHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class EmailProcessor extends AbstractJob {

    @Value("${emailProcessor.logEmails}")
    private Boolean logEmails;

    @Autowired
    private DB db;

    @Autowired
    private EmailDeliveryMetrics emailDeliveryMetrics;

    @Autowired
    private EmailDelivery emailDelivery;

    @Autowired
    private EmailDiscWriter emailDiscWriter;

    @Autowired
    private EmailSender emailSender;

    private List<Envelope> queued = new CopyOnWriteArrayList<>();

    private List<Envelope> durableSent = new CopyOnWriteArrayList<>();

    private EmailDelivery.OnDelivery handler = new EmailDelivery.OnDelivery() {

        @Override
        public void onDelivery(String messageId,
                               String envelopeId,
                               Map<String, String> headers,
                               DeliveryType deliveryType,
                               Map<String, Object> bounceCause) {

            log.info("email has been delivered: messageId={}, envelopeId={}, headers={}, deliveryType={}, bounceCause={}",
                     messageId, envelopeId, headers, deliveryType, bounceCause);

            emailDeliveryMetrics.process(messageId, envelopeId, deliveryType, bounceCause);

            EnvelopeType envelopeType = EnvelopeType.fromEnvelopeId(envelopeId);

            if (envelopeType.isDurable()) {
                emailDiscWriter.emailDelivered(messageId, envelopeId, deliveryType, bounceCause);
            }

            if ((envelopeType.isReportSuccess() && deliveryType == DeliveryType.Delivery) ||
                    (envelopeType.isReportFailure() && deliveryType == DeliveryType.Bounce)) {

                try {
                    reportMessageDelivery(headers, deliveryType);
                } catch (Exception e) {
                    log.error("error occurred while trying to add user notification about email delivery status", e);
                }
            }

            durableSent.removeIf(envelope -> envelope.getEnvelopeId().equals(envelopeId));
        }

        private void reportMessageDelivery(Map<String, String> headers, DeliveryType deliveryType) {

            String userUuid = headers.get("userUuid");
            String recipient = headers.get("recipient");
            String title = headers.get("title");

            db.withLoadedAccountByUuid(userUuid, account -> {

                new UserDataHelper(account.getUserData()).addNotification(
                        createDeliveryNotification(deliveryType, recipient, title));

            });

        }

        private String createDeliveryNotification(DeliveryType deliveryType, String recipient, String title) {
            if (deliveryType == DeliveryType.Delivery) {
                return "Message '" + title + "' has been delivered to " + recipient + " mailbox";
            } else {
                return "Could not deliver message '" + title + "' to " + recipient + " mailbox";
            }
        }

    };

    @Scheduled(fixedDelay = 2500)
    public void process() {
        super.process();
    }

    @Override
    public void processImpl() {
        try {
            sendEmails();
        } catch (Exception e) {
            log.error("problem while sending emails", e);
            reportError("sendEmails", e);
        }

        try {
            checkDeliveredEmails();
        } catch (Exception e) {
            log.error("problem while checking email deliveries", e);
            reportError("checkDeliveredEmails", e);
        }

    }

    private void sendEmails() {
        if (queued.isEmpty()) {
            return;
        }
        log.info("there are {} emails to send", queued.size());

        for (Envelope envelope : queued) {

            String messageId = emailSender.sendEmail(envelope);

            //can't remove by Iterator, as queued is CopyOnWriteArrayList
            queued.remove(envelope);

            if (envelope.getType().isDurable()) {
                emailDiscWriter.envelopeSent(envelope, messageId);
                durableSent.add(envelope);
            }

        }
    }

    public void enqueue(Envelope envelope) {
        log.info("enqueueing envelope: {}", envelope.getEnvelopeId());

        if (logEmails) {
            log.info("envelope details: {}", envelope.toString());
        }

        if (envelope.getType().isDurable()) {
            emailDiscWriter.envelopeEnqueued(envelope);
        }

        queued.add(envelope);
    }

    private void checkDeliveredEmails() {
        if (durableSent.isEmpty()) {
            return;
        }
        log.info("checking delivery queue for {} emails", durableSent.size());
        emailDelivery.process(handler);
    }

}
