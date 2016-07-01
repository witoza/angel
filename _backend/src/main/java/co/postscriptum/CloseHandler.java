package co.postscriptum;

import co.postscriptum.db.DB;
import co.postscriptum.jobs.EmailProcessor;
import co.postscriptum.web.PreRestFilter;
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
    private PreRestFilter preRestFilter;

    @Autowired
    private DB db;

    @Autowired
    private EmailProcessor emailProcessor;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("GENTLE SHUTDOWN BEGIN");

        log.info("shutdown in-flight requests");
        try {
            preRestFilter.shutdownAndAwaitTermination();
        } catch (InterruptedException e) {
            log.error("interrupted while waiting for web requests to finish");
        }
        log.info("shutdown jobs executor");
        scheduledExecutorService.shutdown();
        try {
            scheduledExecutorService.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("interrupted while waiting for jobs to finish");
        }

        log.info("invoke job manually last time");

        emailProcessor.process();

        log.info("shutdown db");
        db.shutdown();

        log.info("GENTLE SHUTDOWN END");

    }
}
