package co.postscriptum.security;

import co.postscriptum.internal.Utils;
import lombok.Value;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

@Value
public class RequestMetadata {

    private long timestamp;

    private String userAgent;

    private String remoteIp;

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
