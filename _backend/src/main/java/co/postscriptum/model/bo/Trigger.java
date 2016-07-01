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

    //time when automated process marks as ready to be released
    private long readyToBeReleasedTime;

    //time when automated process, or admin, really releases messages
    private long haveBeenReleasedTime;

    public enum Stage {
        beforeX,
        afterXbeforeY,
        afterYbeforeZ,
        afterZbeforeW,
        released,
    }

}
