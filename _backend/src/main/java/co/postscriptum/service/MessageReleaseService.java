package co.postscriptum.service;

import co.postscriptum.email.Envelope;
import co.postscriptum.email.EnvelopeType;
import co.postscriptum.internal.ReleasedMessagesDetails;
import co.postscriptum.internal.Utils;
import co.postscriptum.job.EmailProcessor;
import co.postscriptum.model.bo.Lang;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.ReleaseItem;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.HashMap;
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

    public ReleasedMessagesDetails releaseMessages(UserData userData, Optional<SecretKey> userEncryptionKeyOpt) {

        log.info("Releasing outbox messages for user.uuid: {}, hasUserEncryptionKey: {}",
                 userData.getUser().getUuid(),
                 userEncryptionKeyOpt.isPresent());

        ReleasedMessagesDetails details = new ReleasedMessagesDetails();

        List<Message> outboxMessages = getOutboxMessages(userData);
        if (outboxMessages.isEmpty()) {
            log.info("Nothing to send in outbox");
            return details;
        }

        outboxMessages.forEach(message -> {
            details.add(releaseMessage(userData, message, userEncryptionKeyOpt));
        });


        return details;
    }

    public void sendToOwnerReleasedMessageSummary(UserData userData, ReleasedMessagesDetails details) {
        log.info("Sending email to user.uuid: {} about released messages", userData.getUser().getUuid());

        Map<String, Object> context = new HashMap<>();
        context.put("release_msgs_report", toHumanReadable(details, userData.getInternal().getLang()));

        emailProcessor.enqueue(envelopeCreatorService.create(EnvelopeType.RELEASE_SUMMARY,
                                                             userData,
                                                             userData.getUser().getUsername(),
                                                             "sent_messages_summary",
                                                             context));
    }

    public String toHumanReadable(ReleasedMessagesDetails releaseDetails, Lang lang) {
        if (releaseDetails.getDetails().isEmpty()) {
            return "Nothing to send in Outbox";
        }

        String i18nValidRecipient = i18n.translate(lang, "%sent_messages_summary.message_about_to_send%");

        String i18nInvalidRecipient = i18n.translate(lang, "%sent_messages_summary.message_cant_sent%");

        String i18nMessageTitle = i18n.translate(lang, "%sent_messages_summary.message%");

        StringBuilder sb = new StringBuilder();

        releaseDetails.getDetails().forEach(releasedMessage -> {
            sb.append(i18nMessageTitle + " '" + releasedMessage.getMessageTitle() + "':");

            if (releasedMessage.getSentToRecipients().isEmpty() && releasedMessage.getInvalidRecipients().isEmpty()) {
                sb.append("\nNo recipients");
            }

            releasedMessage.getSentToRecipients().forEach(recipient -> {
                sb.append("\n - " + recipient + ": " + i18nValidRecipient);
            });

            releasedMessage.getInvalidRecipients().forEach(recipient -> {
                sb.append("\n - " + recipient + ": " + i18nInvalidRecipient);
            });

        });

        return sb.toString();
    }

    private ReleasedMessagesDetails.ReleasedMessage releaseMessage(UserData userData, Message message, Optional<SecretKey> userEncryptionKeyOpt) {

        log.info("Releasing message.uuid: {} to recipients: {}", message.getUuid(), message.getRecipients());

        if (message.getType() != Message.Type.outbox) {
            throw new IllegalArgumentException("Can't release not outbox type messages");
        }

        Lang lang = ObjectUtils.firstNonNull(message.getLang(), userData.getInternal().getLang());

        ReleasedMessagesDetails.ReleasedMessage releasedMessage = new ReleasedMessagesDetails.ReleasedMessage(message.getTitle());

        for (String originalRecipient : message.getRecipients()) {

            String recipient = originalRecipient.trim();

            if (!Utils.isValidEmail(recipient)) {
                releasedMessage.getInvalidRecipients().add(recipient);
                continue;
            }

            log.info("Valid recipient: {}", recipient);

            ReleaseItem release = message.addReleaseItem(recipient);

            Map<String, Object> context = new HashMap<>();
            context.put("msg", message);
            context.put("lang", lang);
            context.put("release", release);

            userEncryptionKeyOpt.ifPresent(userEncryptionKey -> {

                SecretKey recipientKey = AESKeyUtils.generateRandomKey();

                release.setUserEncryptionKeyEncodedByRecipientKey(AESGCMUtils.encrypt(recipientKey, userEncryptionKey.getEncoded()));

                // recipientKey is only present in email
                context.put("recipientKey", Utils.urlEncode(Utils.base64encode(recipientKey.getEncoded())));

            });

            Envelope messageEnvelope = envelopeCreatorService.create(EnvelopeType.RELEASE_ITEM,
                                                                     userData,
                                                                     recipient,
                                                                     "send_message_to_recipient",
                                                                     context);
            release.setEnvelopeId(messageEnvelope.getEnvelopeId());

            emailProcessor.enqueue(messageEnvelope);

            releasedMessage.getSentToRecipients().add(recipient);

        }

        return releasedMessage;
    }

    private List<Message> getOutboxMessages(UserData userData) {
        return userData.getMessages()
                       .stream()
                       .filter(m -> m.getType() == Message.Type.outbox)
                       .collect(Collectors.toList());
    }

}
