package co.postscriptum.model.bo;

import co.postscriptum.security.AESGCMEncrypted;
import lombok.Data;

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

    public enum Type {
        outbox,
        drafts
    }

}
