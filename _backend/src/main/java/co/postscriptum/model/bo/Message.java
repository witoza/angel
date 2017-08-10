package co.postscriptum.model.bo;

import co.postscriptum.exception.BadRequestException;
import co.postscriptum.exception.ExceptionBuilder;
import co.postscriptum.internal.Utils;
import co.postscriptum.security.AESGCMEncrypted;
import co.postscriptum.security.AESGCMEncryptedByPassword;
import co.postscriptum.security.AESGCMUtils;
import lombok.Data;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Data
public class Message {

    private String uuid;

    private long creationTime;

    private long updateTime;

    private Lang lang;

    private Type type;

    private String title;

    private List<String> recipients;

    private List<String> attachments;

    private AESGCMEncrypted content;

    // if present, means content inside 'content' is encrypted by 'encryption'
    private PasswordEncryption encryption;

    private Release release;

    public void setPassword(SecretKey encryptionKey, String newMsgPassword, String newMsgPasswordHint) {
        String plaintextContent = getContent(encryptionKey, null);

        AESGCMEncryptedByPassword passwordEncrypted = AESGCMUtils.encryptByPassword(newMsgPassword, plaintextContent.getBytes());

        setContent(encryptionKey, passwordEncrypted.getEncrypted());
        setEncryption(PasswordEncryption.builder()
                                        .hint(newMsgPasswordHint)
                                        .salt(passwordEncrypted.getPasswordSalt())
                                        .iv(passwordEncrypted.getEncrypted().getIv())
                                        .build());
    }

    public void removePassword(SecretKey encryptionKey, String encryptionPassword) {
        setContent(encryptionKey, getContent(encryptionKey, encryptionPassword));
        setEncryption(null);
    }

    public void changePassword(SecretKey encryptionKey, String oldMsgPassword, String newMsgPassword, String newMsgPasswordHint) {
        removePassword(encryptionKey, oldMsgPassword);
        setPassword(encryptionKey, newMsgPassword, newMsgPasswordHint);
    }

    public void setContent(SecretKey encryptionKey, AESGCMEncrypted content) {
        setContent(buildMessageContent(encryptionKey, content.getCt()));
    }

    public void setContent(SecretKey encryptionKey, String content) {
        setContent(buildMessageContent(encryptionKey, content));
    }

    public String getContent(SecretKey encryptionKey, String encryptionPassword) {
        byte[] raw = decryptFirstLayerContent(encryptionKey);
        if (getEncryption() == null) {
            return Utils.asString(raw);
        }
        if (encryptionPassword == null) {
            throw new BadRequestException("You have to provide encryptionPassword to open that message");
        }
        byte[] content = AESGCMUtils.decryptByPassword(encryptionPassword, getEncryption(), raw);
        return Utils.asString(content);
    }

    public byte[] decryptFirstLayerContent(SecretKey encryptionKey) {
        return AESGCMUtils.decrypt(encryptionKey, getContent(), aads());
    }

    private String[] aads() {
        return new String[]{
                getUuid(),
                getTitle(),
                getRecipients().toString(),
        };
    }

    private AESGCMEncrypted buildMessageContent(SecretKey encryptionKey, byte[] content) {
        return AESGCMUtils.encrypt(encryptionKey, content, aads());
    }

    private AESGCMEncrypted buildMessageContent(SecretKey encryptionKey, String content) {
        return buildMessageContent(encryptionKey, content.getBytes(StandardCharsets.UTF_8));
    }

    public ReleaseItem addReleaseItem(String recipient) {
        if (getRelease() == null) {
            setRelease(Release.builder()
                              .releaseTime(System.currentTimeMillis())
                              .items(new ArrayList<>())
                              .build());
        }

        ReleaseItem releaseItem = new ReleaseItem();
        releaseItem.setKey(Utils.randKey("RK"));
        releaseItem.setReminders(new ArrayList<>());
        releaseItem.setRecipient(recipient);

        getRelease().getItems().add(releaseItem);
        return releaseItem;
    }

    public ReleaseItem requireReleaseItem(String releaseKey) {
        return release.getItems()
                      .stream()
                      .filter(ri -> ri.getKey().equals(releaseKey))
                      .findFirst()
                      .orElseThrow(ExceptionBuilder.missingObject(ReleaseItem.class, "releaseKey=" + releaseKey));
    }

    public enum Type {
        outbox,
        drafts
    }

}
