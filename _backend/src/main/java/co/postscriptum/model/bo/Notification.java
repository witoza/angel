package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Notification {

    private final String uuid;
    private final long createdTime;
    private final String msg;
    private boolean read;

}
