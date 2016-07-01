package co.postscriptum.security;

import co.postscriptum.internal.Utils;
import lombok.Getter;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

@Getter
public class RequestMetadata {

    private final long time;
    private final String userAgent;
    private final String remoteIp;

    public RequestMetadata(HttpServletRequest request) {
        this.time = System.currentTimeMillis();
        this.userAgent = request.getHeader("user-agent");
        this.remoteIp = Utils.getRemoteIp(request);
    }

    public String getRequestDetails() {
        return String.join("\n",
                           Arrays.asList(
                                   "time: " + Utils.format(Utils.fromTimestamp(time)),
                                   "ip address: " + remoteIp,
                                   "user-agent: " + userAgent));

    }
}
