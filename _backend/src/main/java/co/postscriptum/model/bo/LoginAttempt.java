package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginAttempt {

    private final String ip;
    private final String type;
    private final long time;

}
