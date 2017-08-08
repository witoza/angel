package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LoginAttempt {

    private String ip;

    private String type;

    private long time;

}
