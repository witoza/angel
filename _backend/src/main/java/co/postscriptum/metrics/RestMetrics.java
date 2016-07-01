package co.postscriptum.metrics;

import co.postscriptum.internal.Utils;
import lombok.Synchronized;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class RestMetrics {

    private final RequestsByTimeMetrics requestsByTimeMetrics = new RequestsByTimeMetrics();
    private final Map<Integer, AtomicInteger> httpCodes = new TreeMap<>();
    private final Map<String, MethodUsageInfo> restUsage = new TreeMap<>();
    private final List<RequestInfo> currentRequests = new ArrayList<>();

    private RequestInfo requireRequestInfo(HttpServletRequest request) {
        RequestInfo requestInfo = (RequestInfo) request.getAttribute("RequestInfo");
        if (requestInfo == null) {
            throw new IllegalStateException("current thread does not have RequestInfo object");
        }
        return requestInfo;
    }

    @Synchronized
    public void requestStart(HttpServletRequest request) {

        requestsByTimeMetrics.newRequest();

        RequestInfo ri = new RequestInfo();
        ri.reqId = MDC.get("reqId");
        ri.method = request.getMethod() + " " + request.getRequestURI();
        ri.requestStartTm = System.currentTimeMillis();
        ri.threadId = Thread.currentThread().getId() + "/" + Thread.currentThread().getName();

        request.setAttribute("RequestInfo", ri);

        currentRequests.add(ri);
    }

    @Synchronized
    public void requestEnds(HttpServletRequest request, HttpServletResponse response) {

        RequestInfo ri = requireRequestInfo(request);
        ri.requestFinishTm = System.currentTimeMillis();
        ri.httpStatusCode = response.getStatus();

        request.removeAttribute("RequestInfo");
        currentRequests.remove(ri);

        process(ri);
    }

    @Synchronized
    public void reportException(HttpServletRequest request, Exception exception) {
        requireRequestInfo(request).exception = exception;
    }

    private void process(RequestInfo requestInfo) {
        int et = (int) (requestInfo.requestFinishTm - requestInfo.requestStartTm);

        restUsage.putIfAbsent(requestInfo.method, new MethodUsageInfo(requestInfo.method));
        restUsage.get(requestInfo.method).addRequest(requestInfo.exception, et);

        httpCodes.putIfAbsent(requestInfo.httpStatusCode, new AtomicInteger(0));
        httpCodes.get(requestInfo.httpStatusCode).incrementAndGet();

    }

    @Synchronized
    public String dump() {
        long t1 = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("------ " + this.getClass().getSimpleName() + " ------\n\n");

        sb.append(new JVMMetrics().dump()).append("\n")
          .append("[http responses]\n");

        httpCodes.forEach((httpCode, quantity) -> {
            sb.append(httpCode + "/" + HttpStatus.valueOf(httpCode).getReasonPhrase() + " = " + quantity)
              .append("\n");
        });

        sb.append("\n[in flight requests]\n");

        currentRequests.forEach(ri -> {
            sb.append("reqId=" + ri.reqId + ", method=" + ri.method + ", threadId=" + ri.threadId + ", requestStartTm="
                              + Utils.format(ri.requestStartTm) + "\n");
        });

        sb.append("\n[REST usage]\n")
          .append(restUsage.values().stream().map(MethodUsageInfo::toString).collect(Collectors.joining("\n")))
          .append("\n\n")
          .append(requestsByTimeMetrics.dump())
          .append("\n------------------\n")
          .append("generated in " + (System.currentTimeMillis() - t1) + "ms\n");

        return sb.toString();
    }

    private static class RequestInfo {
        String reqId;
        String method;
        long requestStartTm;
        long requestFinishTm;
        Exception exception;
        int httpStatusCode;
        String threadId;
    }

}
