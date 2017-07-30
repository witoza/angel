package co.postscriptum.security;

import co.postscriptum.internal.Utils;
import lombok.Getter;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

@Getter
public class RequestMetadata {

    private final long timestamp;

    private final String userAgent;

    private final String remoteIp;

    public RequestMetadata(HttpServletRequest request) {
        this.timestamp = System.currentTimeMillis();
        this.userAgent = request.getHeader("user-agent");
        this.remoteIp = Utils.getRemoteIp(request);
    }

    public String getRequestDetails() {
        return String.join("\n",
                           Arrays.asList(
                                   "timestamp: " + Utils.format(Utils.fromTimestamp(timestamp)),
                                   "ip address: " + remoteIp,
                                   "user-agent: " + userAgent));

    }

}
