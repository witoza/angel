package co.postscriptum.metrics;

import co.postscriptum.internal.Utils;
import org.slf4j.MDC;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MethodUsageInfo {

    private final String method;

    private final List<String> lastExceptions = new ArrayList<>();

    private int total = 0;

    private int err = 0;

    private int sampleId = 0;

    private int[] timeSamples = new int[10];

    private int maxTm = -1;

    public MethodUsageInfo(String method) {
        this.method = method;
    }

    public void addRequest(Exception exception, int elapsedTime) {
        total++;

        if (exception != null) {
            err++;
            lastExceptions.add(exceptionToString(exception));
            if (lastExceptions.size() > 3) {
                lastExceptions.remove(0);
            }
        }

        if (maxTm == -1 || maxTm < elapsedTime) {
            maxTm = elapsedTime;
        }

        timeSamples[sampleId] = elapsedTime;
        sampleId = (sampleId + 1) % timeSamples.length;

    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append(method + ": total=" + total);
        if (err > 0) {
            sb.append(", err=" + err);
        }
        if (total > timeSamples.length) {
            sb.append(", avgTm=" + getAvg());
        }
        sb.append(", maxTm=" + maxTm);

        if (!lastExceptions.isEmpty()) {
            sb.append(", last 3 exceptions:\n")
              .append(lastExceptions.stream().collect(Collectors.joining("\n\n")));
        }
        return sb.toString();
    }

    private static String exceptionToString(Exception exception) {
        StringWriter sw = new StringWriter();

        exception.printStackTrace(new PrintWriter(sw));

        String[] lines = sw.toString().split("\n");
        String stackTrace =
                lines[0] + "\n" +
                        Arrays.stream(lines)
                              .filter(li -> li.contains("co.postscriptum"))
                              .filter(li -> !li.contains("(<generated>)"))
                              .map(li -> "  " + li.trim())
                              .collect(Collectors.joining("\n"));

        String date = Utils.format(System.currentTimeMillis());
        String reqId = MDC.get("reqId");

        return "date=" + date + ", reqId=" + reqId + ", stackTrace=" + stackTrace;
    }

    private long getAvg() {
        int sum = 0;
        for (int val : timeSamples) {
            sum += val;
        }
        return sum / timeSamples.length;
    }
}