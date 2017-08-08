package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Data;

import java.time.temporal.ChronoUnit;

@Data
@Builder
public class Trigger {

    private Boolean enabled;

    private Stage stage;

    private ChronoUnit timeUnit;

    private Integer x;

    private Integer y;

    private Integer z;

    private Integer w;

    // time when automated process or admin released messages
    private long releasedTime;

    public enum Stage {

        BEFORE_X,
        AFTER_X_BEFORE_Y,
        AFTER_Y_BEFORE_Z,
        AFTER_Z_BEFORE_RELEASE,
        RELEASED,

    }

}
