package co.postscriptum.web;

import co.postscriptum.db.Account;
import co.postscriptum.db.DB;
import co.postscriptum.exception.InternalException;
import co.postscriptum.internal.Utils;
import co.postscriptum.metrics.RestMetrics;
import co.postscriptum.model.bo.User.Role;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.security.MyAuthenticationToken;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@AllArgsConstructor
public class IncomingRequestFilter extends GenericFilterBean {

    public static final int MAX_CONCURRENT_REQUESTS = 10;

    private final AtomicInteger requestId = new AtomicInteger(0);

    private final AtomicBoolean shouldAcceptNewRequests = new AtomicBoolean(true);

    private final AtomicInteger numberOfInFlightTransactions = new AtomicInteger(0);

    private final RestMetrics restMetrics;

    private final DB db;

    public void shutdownAndAwaitTermination() throws InterruptedException {
        log.info("do not accept any more requests");
        shouldAcceptNewRequests.set(false);

        while (numberOfInFlightTransactions.get() != 0) {
            log.info("waiting for {} requests to finish", numberOfInFlightTransactions.get());
            Thread.sleep(1000);
        }

    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {

        if (!shouldAcceptNewRequests.get()) {
            throw new InternalException("Application is in the maintenance mode, please come back later");
        }

        if (numberOfInFlightTransactions.get() > MAX_CONCURRENT_REQUESTS) {
            throw new InternalException("Traffic is to high, please come back later");
        }

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        final String requestInfo = request.getMethod() + " " + getFullURL(request) + "; ip=" + Utils.getRemoteIp(request);

        try {
            numberOfInFlightTransactions.incrementAndGet();

            long startTime = System.currentTimeMillis();
            MDC.put("reqId", "" + requestId.incrementAndGet());

            try {
                restMetrics.requestStart(request);

                Authentication auth = AuthenticationHelper.getAuthentication();
                if (auth instanceof MyAuthenticationToken) {

                    MyAuthenticationToken myAuthenticationToken = (MyAuthenticationToken) auth;

                    if (myAuthenticationToken.getRole() == Role.admin) {
                        MDC.put("reqType", "admin");
                    }

                    log.info("> " + requestInfo + "; username=" + myAuthenticationToken.getName());

                    Account account = db.requireAccountByUsername(auth.getName());
                    try {
                        account.lock();
                        UserData userData = account.getUserData();
                        log.debug("updating user lastAccess time");
                        userData.getUser().setLastAccess(System.currentTimeMillis());

                        chain.doFilter(request, response);
                    } finally {
                        account.unlock();
                    }

                } else {
                    log.info("> " + requestInfo + "; unauthed");
                    chain.doFilter(request, response);
                }

            } catch (Exception e) {
                log.error("internal error", e);
                restMetrics.reportException(request, e);
                throw new InternalException("Our servers are experiencing issues, please come back later");
            } finally {
                restMetrics.requestEnds(request, response);
                log.info("< done in " + (System.currentTimeMillis() - startTime) + "ms, status=" + response.getStatus());
            }

        } finally {
            MDC.clear();
            numberOfInFlightTransactions.decrementAndGet();
        }

    }

    private static String getFullURL(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();

        if (queryString == null) {
            return requestURI;
        } else {
            return requestURI + "?" + queryString;
        }
    }

}
