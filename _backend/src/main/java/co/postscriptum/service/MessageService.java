package co.postscriptum.service;

import co.postscriptum.controller.MessageController.AddMsgDTO;
import co.postscriptum.controller.MessageController.UpdateMsgDTO;
import co.postscriptum.exception.ForbiddenException;
import co.postscriptum.exception.InternalException;
import co.postscriptum.internal.MessageContentUtils;
import co.postscriptum.model.BO2DTOConverter;
import co.postscriptum.model.bo.DataFactory;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.PasswordEncryption;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.dto.MessageDTO;
import co.postscriptum.security.AESGCMEncrypted;
import co.postscriptum.security.AESGCMEncryptedByPassword;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class MessageService {

    public Message addMessage(UserData userData, SecretKey userEncryptionKey, AddMsgDTO params) {

        new UserDataHelper(userData).verifyQuotaNotExceeded(params.getContent().length());

        // set some sane limit
        if (userData.getMessages().size() > 100) {
            throw new InternalException("too many messages");
        }

        Message message = DataFactory.newMessage();

        message.setType(params.getType());
        message.setTitle(params.getTitle());
        message.setAttachments(params.getAttachments());
        message.setRecipients(params.getRecipients());
        message.setContent(buildMessageContent(message, userEncryptionKey, params.getContent()));

        userData.getMessages().add(message);

        log.info("Message has been added");

        return message;
    }

    public void deleteMessage(UserData userData, String msgUuid) {
        Message message = requireMessageByUuid(userData, msgUuid);

        verifyMessageHasNoEncryptedFiles(userData, message);

        userData.getMessages().remove(message);
    }

    public Message updateMessage(UserData userData, SecretKey userEncryptionKey, UpdateMsgDTO params) {

        Message message = requireMessageByUuid(userData, params.getUuid());

        message.setUpdateTime(System.currentTimeMillis());

        message.setLang(params.getLang());
        if (params.getType() != null) {
            message.setType(params.getType());
        }
        if (params.getTitle() != null) {
            message.setTitle(params.getTitle());
        }
        if (params.getAttachments() != null) {
            message.setAttachments(params.getAttachments());
        }
        if (params.getRecipients() != null) {
            message.setRecipients(params.getRecipients());
        }

        if (params.getContent() != null) {

            String content = params.getContent();

            new UserDataHelper(userData).verifyQuotaNotExceeded(content.length() - message.getContent().getCt().length);

            if (message.getEncryption() == null) {

                message.setContent(buildMessageContent(message, userEncryptionKey, content));

            } else {

                SecretKey key = AESKeyUtils.deriveKey(params.getEncryption_passwd(), message.getEncryption().getSalt());

                AESGCMEncrypted passwordEncrypted = AESGCMUtils.encrypt(key, content.getBytes());

                message.setContent(buildMessageContent(message, userEncryptionKey, passwordEncrypted));

                message.getEncryption().setIv(passwordEncrypted.getIv());
            }

        }

        log.info("Message uuid: {} has been updated", message.getUuid());

        return message;
    }

    public Message removePassword(UserData userData, SecretKey userEncryptionKey, String msgUuid, String msgPassword) {

        Message message = requireMessageByUuid(userData, msgUuid);

        verifyMessageHasNoEncryptedFiles(userData, message);

        message.setContent(buildMessageContent(message, userEncryptionKey,
                                               getMessageContent(message, userEncryptionKey, msgPassword)));
        message.setEncryption(null);

        return message;
    }

    public Message setPassword(UserData userData, SecretKey userEncryptionKey, String msgUuid, String oldMsgPassword, String newMsgPassword, String newMsgPasswordHint) {

        Message message = requireMessageByUuid(userData, msgUuid);

        verifyMessageHasNoEncryptedFiles(userData, message);

        String plaintextContent = getMessageContent(message, userEncryptionKey, oldMsgPassword);

        AESGCMEncryptedByPassword passwordEncrypted =
                AESGCMUtils.encryptByPassword(newMsgPassword, plaintextContent.getBytes());

        message.setContent(buildMessageContent(message, userEncryptionKey, passwordEncrypted.getEncrypted()));
        message.setEncryption(PasswordEncryption.builder()
                                                .hint(newMsgPasswordHint)
                                                .salt(passwordEncrypted.getPasswordSalt())
                                                .iv(passwordEncrypted.getEncrypted().getIv())
                                                .build());
        return message;
    }

    private void verifyMessageHasNoEncryptedFiles(UserData userData, Message message) {

        long total = userData.getFiles()
                             .stream()
                             .filter(f -> message.getUuid().equals(f.getBelongsTo()))
                             .count();

        if (total > 0) {
            throw new ForbiddenException(
                    "Can't proceed because there is/are " + total +
                            " encrypted files that belongs to that message. To continue you have to decrypt or delete them first.");
        }

    }

    private MessageDTO convertToDtoSimplified(UserData userData, Message message) {

        MessageDTO mdto = BO2DTOConverter.toMessageDTO(message);
        mdto.setFiles(message.getAttachments()
                             .stream()
                             .map(attachment -> new UserDataHelper(userData).requireFileByUuid(attachment))
                             .map(f -> BO2DTOConverter.toFileDTO(userData, f))
                             .collect(Collectors.toList()));
        return mdto;
    }

    public MessageDTO convertToDto(UserData userData, Message message) {

        MessageDTO mdto = convertToDtoSimplified(userData, message);
        mdto.getFiles().forEach(fileDTO -> {
            fileDTO.setMessages(userData
                                        .getMessages()
                                        .stream()
                                        .filter(m -> m.getAttachments().contains(fileDTO.getUuid()))
                                        .map(BO2DTOConverter::toMessageDTO)
                                        .collect(Collectors.toList()));
        });

        return mdto;
    }

    public Message requireMessageByUuid(UserData userData, String msgUuid) {
        return new UserDataHelper(userData).requireMessageByUuid(msgUuid);
    }

    public String getMessageContent(Message message, SecretKey userEncryptionKey, String encryptionPassword) {
        return MessageContentUtils.getMessageContent(message, userEncryptionKey, encryptionPassword);
    }

    private AESGCMEncrypted buildMessageContent(Message message, SecretKey userEncryptionKey, AESGCMEncrypted content) {
        return MessageContentUtils.buildMessageContent(message, userEncryptionKey, content.getCt());
    }

    private AESGCMEncrypted buildMessageContent(Message message, SecretKey userEncryptionKey, String content) {
        return MessageContentUtils.buildMessageContent(message, userEncryptionKey, content);
    }

}
