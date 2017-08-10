package co.postscriptum.model.bo;

import co.postscriptum.exception.BadRequestException;
import co.postscriptum.exception.ExceptionBuilder;
import co.postscriptum.internal.Utils;
import co.postscriptum.security.AESGCMEncrypted;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;

@Data
public class ReleaseItem {

    public static final String CAN_NOT_CONTACT_RECIPIENT = "can_not_contact_recipient";

    private String key;

    private String recipient;

    private String envelopeId;

    private AESGCMEncrypted userEncryptionKeyEncodedByRecipientKey;

    private long firstTimeAccess;

    private List<Reminder> reminders;

    public Reminder requireReminder(String remainderUuid) {
        return reminders.stream()
                        .filter(r -> r.getUuid().equals(remainderUuid))
                        .findFirst()
                        .orElseThrow(ExceptionBuilder.missingObject(Reminder.class, "uuid=" + remainderUuid));
    }

    public boolean adminAbleToContactRecipient() {
        Optional<Reminder> lastReminder = Utils.getLast(reminders);
        if (lastReminder.isPresent()) {
            if (StringUtils.equalsIgnoreCase(lastReminder.get().getInput(), CAN_NOT_CONTACT_RECIPIENT)) {
                return false;
            }
        }
        return true;
    }

    @Data
    public static class Reminder {

        private String uuid;

        private long createdTime;

        private long resolvedTime;

        private String input;

        private boolean resolved;

        public void resolve(String input) {
            if (resolved) {
                throw new BadRequestException("Reminder already resolved");
            }
            resolvedTime = System.currentTimeMillis();
            resolved = true;
            this.input = input;
        }

    }

}
