package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Notification {

    private String uuid;

    private long createdTime;

    private String msg;

    private boolean read;

}
