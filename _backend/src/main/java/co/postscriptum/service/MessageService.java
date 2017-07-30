package co.postscriptum.service;

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
import co.postscriptum.web.MessageRest.AddMsgDTO;
import co.postscriptum.web.MessageRest.UpdateMsgDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class MessageService {

    private final LoggedUserService loggedUserService;

    public Message requireMessageByUuid(String msgUuid) {
        return loggedUserService.requireMessageByUuid(msgUuid);
    }

    public String getMessageContent(Message message, String encryptionPassword) {
        return MessageContentUtils.getMessageContent(message, loggedUserService.requireUserEncryptionKey(), encryptionPassword);
    }

    private AESGCMEncrypted buildMessageContent(Message message, AESGCMEncrypted content) {
        return MessageContentUtils.buildMessageContent(message, loggedUserService.requireUserEncryptionKey(), content.getCt());
    }

    private AESGCMEncrypted buildMessageContent(Message message, String content) {
        return MessageContentUtils.buildMessageContent(message, loggedUserService.requireUserEncryptionKey(), content);
    }

    public Message addMessage(AddMsgDTO params) {
        loggedUserService.verifyQuotaNotExceeded(params.getContent().length());

        //set some sane limit
        if (loggedUserService.requireUserData().getMessages().size() > 100) {
            throw new InternalException("too many messages");
        }

        Message message = DataFactory.newMessage();

        message.setType(params.getType());
        message.setTitle(params.getTitle());
        message.setAttachments(params.getAttachments());
        message.setRecipients(params.getRecipients());
        message.setContent(buildMessageContent(message, params.getContent()));

        loggedUserService.requireUserData().getMessages().add(message);

        log.info("msg has been added");

        return message;
    }

    public Message removePassword(String uuid, String encryptionPassword) {

        Message message = requireMessageByUuid(uuid);

        verifyMessageHasNoEncryptedFiles(message);

        message.setContent(buildMessageContent(message, getMessageContent(message, encryptionPassword)));
        message.setEncryption(null);

        return message;
    }

    public Message setPassword(String uuid, String encryptionPassword, String newEncryptionPassword, String hint) {

        Message message = requireMessageByUuid(uuid);

        verifyMessageHasNoEncryptedFiles(message);

        String plaintextContent = getMessageContent(message, encryptionPassword);

        AESGCMEncryptedByPassword passwordEncrypted =
                AESGCMUtils.encryptByPassword(newEncryptionPassword, plaintextContent.getBytes());

        message.setContent(buildMessageContent(message, passwordEncrypted.getEncrypted()));
        message.setEncryption(PasswordEncryption.builder()
                                                .hint(hint)
                                                .salt(passwordEncrypted.getPasswordSalt())
                                                .iv(passwordEncrypted.getEncrypted().getIv())
                                                .build());
        return message;
    }

    public void deleteMessage(String uuid) {
        Message message = requireMessageByUuid(uuid);

        verifyMessageHasNoEncryptedFiles(message);

        loggedUserService.requireUserData().getMessages().remove(message);
    }

    public Message updateMessage(UpdateMsgDTO params) {

        Message message = requireMessageByUuid(params.getUuid());

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

            loggedUserService.verifyQuotaNotExceeded(content.length() - message.getContent().getCt().length);

            if (message.getEncryption() == null) {

                message.setContent(buildMessageContent(message, content));

            } else {

                SecretKey key = AESKeyUtils.deriveKey(params.getEncryption_passwd(), message.getEncryption().getSalt());

                AESGCMEncrypted passwordEncrypted = AESGCMUtils.encrypt(key, content.getBytes());

                message.setContent(buildMessageContent(message, passwordEncrypted));

                message.getEncryption().setIv(passwordEncrypted.getIv());
            }

        }

        log.info("msg {} has been updated", message.getUuid());

        return message;
    }

    private void verifyMessageHasNoEncryptedFiles(Message message) {

        long total = loggedUserService.requireUserData()
                                      .getFiles()
                                      .stream()
                                      .filter(f -> message.getUuid().equals(f.getBelongsTo()))
                                      .count();

        if (total > 0) {
            throw new ForbiddenException("Can't proceed because there is/are " + total
                                                 +
                                                 " encrypted files that belongs to that message. To continue you have to decrypt or delete them first.");
        }

    }

    public List<Message> getMessages() {
        return loggedUserService.requireUserData().getMessages();
    }

    public MessageDTO convertToDtoSimplified(Message message) {

        UserData userData = loggedUserService.requireUserData();

        MessageDTO mdto = BO2DTOConverter.toMessageDTO(message);
        mdto.setFiles(message.getAttachments()
                             .stream()
                             .map(loggedUserService::requireFileByUuid)
                             .map(f -> BO2DTOConverter.toFileDTO(userData, f))
                             .collect(Collectors.toList()));
        return mdto;
    }

    public MessageDTO convertToDto(Message message) {

        UserData userData = loggedUserService.requireUserData();

        MessageDTO mdto = convertToDtoSimplified(message);
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

}
