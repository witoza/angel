package co.postscriptum.model.bo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RequiredAction {

    String uuid;
    Type type;
    long createdTime;
    String userUuid;
    String userUsername;
    Map<String, Object> details;
    Status status;
    List<Resolution> resolutions;

    public enum Status {
        resolved,
        unresolved
    }

    public enum Type {
        storage_increase,
        new_user_password,
        manual_release_messages,
        automatic_release_messages,
        message_not_opened_by_the_recipient,
        account_can_be_removed
    }

    @Data
    public static class Resolution {
        long createdTime;
        String userInput;
        String msg;
    }

}
