package co.postscriptum.model.bo;

import co.postscriptum.security.AESGCMEncrypted;
import lombok.Data;

import java.util.List;

@Data
public class ReleaseItem {

    private String key;

    private String recipient;

    private String envelopeId;

    private AESGCMEncrypted userEncryptionKeyEncodedByRecipientKey;

    private long firstTimeAccess;

    private List<Reminder> reminders;

    @Data
    public static class Reminder {

        private String uuid;

        private long createdTime;

        private long resolvedTime;

        private String input;

        private boolean resolved;

    }

}
