package co.postscriptum;

import co.postscriptum.db.DB;
import co.postscriptum.job.EmailProcessor;
import co.postscriptum.web.IncomingRequestFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CloseHandler implements ApplicationListener<ContextClosedEvent> {

    @Autowired
    @Qualifier("jobExecutor")
    private ScheduledExecutorService scheduledExecutorService;

    @Autowired
    private IncomingRequestFilter incomingRequestFilter;

    @Autowired
    private DB db;

    @Autowired
    private EmailProcessor emailProcessor;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("Close Handler begins");

        log.info("Shutdown in-flight requests");
        try {
            incomingRequestFilter.shutdownAndAwaitTermination();
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for web requests to finish", e);
        }
        log.info("Shutdown jobs executor");
        scheduledExecutorService.shutdown();
        try {
            scheduledExecutorService.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for jobs to finish", e);
        }

        log.info("Invoke email processor job manually last time");

        emailProcessor.process();

        log.info("Shutdown db");
        db.shutdown();

        log.info("Close handler ends");
    }

}

