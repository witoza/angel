package co.postscriptum.job;

import co.postscriptum.metrics.ComponentMetrics;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public abstract class AbstractJob {

    @Autowired
    protected ComponentMetrics componentMetrics;

    private final AtomicInteger jobInstanceId = new AtomicInteger(0);

    public abstract void processImpl();

    protected void after() throws Exception {
    }

    protected void reportError(String method, Exception e) {
        componentMetrics.put(this.getClass(), method, 0, e);
    }

    protected void process() {

        String classSimpleName = this.getClass().getSimpleName();

        Exception thrown = null;
        long t = System.currentTimeMillis();
        try {
            MDC.put("reqType", classSimpleName);
            MDC.put("reqId", classSimpleName + "-" + jobInstanceId.incrementAndGet());
            processImpl();
            after();
        } catch (Exception e) {
            thrown = e;
        } finally {
            componentMetrics.put(this.getClass(), "total", System.currentTimeMillis() - t, thrown);
            MDC.clear();
        }

    }

}
