package co.postscriptum.model.bo;

import co.postscriptum.exception.BadRequestException;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RequiredAction {

    private String uuid;

    private Type type;

    private long createdTime;

    private String userUuid;

    private String userUsername;

    private Map<String, Object> details;

    private Status status;

    private List<Resolution> resolutions;

    public Resolution resolve(String userInput, String msg) {
        if (status == Status.resolved) {
            throw new BadRequestException("Already resolved");
        }
        Resolution resolution = new Resolution();
        resolution.setCreatedTime(System.currentTimeMillis());
        resolution.setUserInput(userInput);
        resolution.setMsg(msg);
        resolutions.add(resolution);
        status = Status.resolved;
        return resolution;
    }

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

        private long createdTime;

        private String userInput;

        private String msg;

    }

}
