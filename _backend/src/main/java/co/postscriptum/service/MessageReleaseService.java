package co.postscriptum.service;

import co.postscriptum.email.Envelope;
import co.postscriptum.email.EnvelopeType;
import co.postscriptum.exception.InternalException;
import co.postscriptum.internal.I18N;
import co.postscriptum.internal.ReleasedMessagesDetails;
import co.postscriptum.internal.Utils;
import co.postscriptum.jobs.EmailProcessor;
import co.postscriptum.model.bo.Lang;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.Release;
import co.postscriptum.model.bo.ReleaseItem;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class MessageReleaseService {

    private final EnvelopeCreatorService envelopeCreatorService;

    private final I18N i18n;

    private final EmailProcessor emailProcessor;

    public static ReleaseItem createReleaseItem(Message message, String recipient) {
        if (message.getRelease() == null) {
            message.setRelease(Release.builder()
                                      .releaseTime(System.currentTimeMillis())
                                      .items(new ArrayList<>())
                                      .build());
        }

        ReleaseItem releaseItem = new ReleaseItem();
        releaseItem.setKey(Utils.randKey("RK"));
        releaseItem.setReminders(new ArrayList<>());
        releaseItem.setRecipient(recipient);

        message.getRelease().getItems().add(releaseItem);

        return releaseItem;
    }

    private List<Message> getOutboxMessages(UserData userData) {
        return userData
                .getMessages()
                .stream()
                .filter(m -> m.getType() == Message.Type.outbox)
                .collect(Collectors.toList());
    }

    public String toHumanReadable(Lang lang, ReleasedMessagesDetails releaseDetails) {
        if (releaseDetails.getDetails().isEmpty()) {
            return "Nothing to send in Outbox";
        }

        String i18n_message = i18n.translate(lang, "%sent_messages_summary.message%");

        StringBuilder sb = new StringBuilder();

        releaseDetails.getDetails().forEach(pair -> {
            String messageTitle = pair.getKey();
            Map<String, String> recipientsStatus = pair.getValue();

            sb.append(i18n_message + " '" + messageTitle + "':");

            if (recipientsStatus.isEmpty()) {
                sb.append("\nNo recipients");
            }

            recipientsStatus.forEach((recipient, status) -> {
                sb.append("\n - " + recipient + ": ").append(i18n.translate(lang, status));
            });

            sb.append("\n\n");

        });

        return sb.toString();
    }

    private Map<String, String> releaseMessage(UserData userData,
                                               Message message,
                                               Optional<SecretKey> userEncryptionKey) {

        log.info("processing release message uuid={} addressed to recipients={}",
                 message.getUuid(), message.getRecipients());

        if (message.getType() != Message.Type.outbox) {
            throw new InternalException("can't release not outbox type messages");
        }

        Lang lang = ObjectUtils.firstNonNull(message.getLang(), userData.getInternal().getLang());

        Map<String, String> recipientsStatus = new LinkedHashMap<>();

        for (String originalRecipient : message.getRecipients()) {

            String recipient = originalRecipient.trim();

            if (!Utils.isValidEmail(recipient)) {
                recipientsStatus.put(recipient, "%sent_messages_summary.message_cant_sent%");
                continue;
            }

            ReleaseItem release = createReleaseItem(message, recipient);

            Map<String, Object> context = new HashMap<>();
            context.put("msg", message);
            context.put("lang", lang);
            context.put("release", release);

            if (userEncryptionKey.isPresent()) {

                SecretKey recipientKey = AESKeyUtils.generateRandomKey();
                release.setUserEncryptionKeyEncodedByRecipientKey(
                        AESGCMUtils.encrypt(recipientKey, userEncryptionKey.get().getEncoded()));

                //key is only present in email
                context.put("recipientKey", Utils.urlEncode(Utils.base64encode(recipientKey.getEncoded())));

            }

            Envelope messageEnvelope = envelopeCreatorService.create(EnvelopeType.RELEASE_ITEM,
                                                                     userData,
                                                                     recipient,
                                                                     "send_message_to_recipient",
                                                                     context);
            release.setEnvelopeId(messageEnvelope.getEnvelopeId());

            emailProcessor.enqueue(messageEnvelope);

            recipientsStatus.put(recipient, "%sent_messages_summary.message_about_to_send%");
        }

        return recipientsStatus;
    }

    public ReleasedMessagesDetails releaseMessages(UserData userData, Optional<SecretKey> userEncryptionKey) {

        log.info("releasing outbox messages for user.uuid={}", userData.getUser().getUuid());

        ReleasedMessagesDetails details = new ReleasedMessagesDetails();

        List<Message> outboxMessages = getOutboxMessages(userData);
        if (outboxMessages.isEmpty()) {
            log.info("nothing to send in outbox");
            return details;
        }

        for (Message message : outboxMessages) {
            details.add(message, releaseMessage(userData, message, userEncryptionKey));
        }

        return details;
    }

    public void sendUserReleasedMessageSummary(UserData userData, ReleasedMessagesDetails details) {

        log.info("sending email to user.uuid={} about released messages", userData.getUser().getUuid());

        Map<String, Object> context = new HashMap<>();
        context.put("release_msgs_report", toHumanReadable(userData.getInternal().getLang(), details));

        emailProcessor.enqueue(envelopeCreatorService.create(EnvelopeType.RELEASE_SUMMARY,
                                                             userData,
                                                             userData.getUser().getUsername(),
                                                             "sent_messages_summary",
                                                             context));
    }

}
