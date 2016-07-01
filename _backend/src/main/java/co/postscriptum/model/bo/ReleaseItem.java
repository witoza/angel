package co.postscriptum.model.bo;

import co.postscriptum.security.AESGCMEncrypted;
import lombok.Data;

import java.util.List;

@Data
public class ReleaseItem {

    String key;
    String recipient;
    String envelopeId;
    AESGCMEncrypted userEncryptionKeyEncodedByRecipientKey;
    long firstTimeAccess;
    List<Reminder> reminders;

    @Data
    public static class Reminder {
        String uuid;
        long createdTime;
        long resolvedTime;
        String input;
        boolean resolved;
    }

}
