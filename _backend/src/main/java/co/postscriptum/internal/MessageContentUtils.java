package co.postscriptum.internal;

import co.postscriptum.exception.BadRequestException;
import co.postscriptum.model.bo.Message;
import co.postscriptum.security.AESGCMEncrypted;
import co.postscriptum.security.AESGCMUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

public class MessageContentUtils {

    public static String[] aads(Message message) {
        return new String[]{
                message.getUuid(),
                message.getTitle(),
                message.getRecipients().toString(),
//        message.getAttachments().toString()
        };
    }

    public static AESGCMEncrypted buildMessageContent(Message message, SecretKey encryptionKey, byte[] content) {
        return AESGCMUtils.encrypt(encryptionKey, content, aads(message));
    }

    public static AESGCMEncrypted buildMessageContent(Message message, SecretKey encryptionKey, String content) {
        return buildMessageContent(message, encryptionKey, content.getBytes(StandardCharsets.UTF_8));
    }

    public static String getMessageContent(Message message, SecretKey encryptionKey, String encryptionPassword) {
        byte[] raw = AESGCMUtils.decrypt(encryptionKey, message.getContent(), aads(message));
        if (message.getEncryption() == null) {
            return Utils.asString(raw);
        }

        if (encryptionPassword == null) {
            throw new BadRequestException("you have to provide encryptionPassword to open that message");
        }

        byte[] content = AESGCMUtils.decryptByPassword(encryptionPassword, message.getEncryption(), raw);
        return Utils.asString(content);
    }

}
