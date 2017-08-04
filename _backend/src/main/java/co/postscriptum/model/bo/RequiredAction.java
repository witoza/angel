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
        USER_STORAGE_INCREASE_REQUEST,
        USER_RESET_PASSWORD_REQUEST,
        REQUIRE_MANUAL_RELEASE_MESSAGES,
        AUTOMATIC_RELEASE_MESSAGES_HAS_BEEN_DONE,
        MESSAGE_NOT_YET_OPENED_BY_THE_RECIPIENT,
        ACCOUNT_CAN_BE_REMOVED
    }

    @Data
    public static class Resolution {
        long createdTime;
        String userInput;
        String msg;
    }

}
