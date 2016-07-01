package co.postscriptum.model.bo;

import co.postscriptum.security.AESGCMEncrypted;
import lombok.Data;

import java.util.List;

@Data
public class Message {

    String uuid;
    long creationTime;
    long updateTime;
    Lang lang;
    Type type;
    String title;
    List<String> recipients;
    Release release;
    List<String> attachments;
    AESGCMEncrypted content;
    // if present, means content inside 'content' is encrypted by 'encryption'
    PasswordEncryption encryption;

    public enum Type {
        outbox,
        drafts
    }

}
