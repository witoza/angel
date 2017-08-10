package co.postscriptum.service;

import co.postscriptum.controller.MessageController.UpdateMsgDTO;
import co.postscriptum.exception.BadRequestException;
import co.postscriptum.exception.ForbiddenException;
import co.postscriptum.model.BO2DTOConverter;
import co.postscriptum.model.bo.DataFactory;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.dto.MessageDTO;
import co.postscriptum.security.AESGCMEncrypted;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class MessageService {

    public Message addMessage(UserData userData,
                              SecretKey userEncryptionKey,
                              @NonNull Message.Type type,
                              @NonNull List<String> recipients,
                              @NonNull List<String> attachments,
                              @NonNull String title,
                              @NonNull String content) {

        userData.verifyQuotaNotExceeded(content.length());

        if (userData.getMessages().size() > 100) {
            throw new BadRequestException("Too many messages");
        }

        Message message = DataFactory.newMessage();
        message.setType(type);
        message.setTitle(title);
        message.setAttachments(new ArrayList<>(attachments));
        message.setRecipients(new ArrayList<>(recipients));
        message.setContent(userEncryptionKey, content);

        userData.getMessages().add(message);

        log.info("Message has been added");

        return message;
    }

    public void deleteMessage(UserData userData, String msgUuid) {
        Message message = userData.requireMessageByUuid(msgUuid);
        verifyMessageHasNoEncryptedFiles(userData, message);
        userData.getMessages().remove(message);
    }

    public Message updateMessage(UserData userData, SecretKey userEncryptionKey, UpdateMsgDTO params) {

        Message message = userData.requireMessageByUuid(params.getUuid());
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

            userData.verifyQuotaNotExceeded(content.length() - message.getContent().getCt().length);

            if (message.getEncryption() == null) {
                message.setContent(userEncryptionKey, content);
            } else {

                SecretKey key = AESKeyUtils.deriveKey(params.getEncryption_passwd(), message.getEncryption().getSalt());

                AESGCMEncrypted passwordEncrypted = AESGCMUtils.encrypt(key, content.getBytes());

                message.setContent(userEncryptionKey, passwordEncrypted);
                message.getEncryption().setIv(passwordEncrypted.getIv());
            }

        }

        log.info("Message uuid: {} has been updated", message.getUuid());
        return message;
    }

    public Message removePassword(UserData userData,
                                  SecretKey userEncryptionKey,
                                  String msgUuid,
                                  String msgPassword) {

        Message message = userData.requireMessageByUuid(msgUuid);
        verifyMessageHasNoEncryptedFiles(userData, message);
        message.removePassword(userEncryptionKey, msgPassword);
        return message;
    }

    public Message setPassword(UserData userData,
                               SecretKey userEncryptionKey,
                               String msgUuid,
                               String oldMsgPassword,
                               String newMsgPassword,
                               String newMsgPasswordHint) {

        Message message = userData.requireMessageByUuid(msgUuid);
        verifyMessageHasNoEncryptedFiles(userData, message);
        message.changePassword(userEncryptionKey, oldMsgPassword, newMsgPassword, newMsgPasswordHint);
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
                             .map(userData::requireFileByUuid)
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

}
