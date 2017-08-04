package co.postscriptum.job;

import co.postscriptum.db.Account;
import co.postscriptum.db.DB;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Stream;

@Slf4j
public abstract class AbstractAccountJob extends AbstractJob {

    @Autowired
    protected DB db;

    public abstract String processAccount(Account account) throws Exception;

    public abstract Stream<Account> getAccountsToTest();

    @Override
    public void processImpl() {

        getAccountsToTest().forEach(account -> {

            long t = System.currentTimeMillis();
            String uuid = account.getUserData().getUser().getUuid();
            String username = account.getUserData().getUser().getUsername();
            Exception thrown = null;

            try {
                account.lock();
                log.info("Processing: user [username: {}, uuid: {}]", username, uuid);
                String result = processAccount(account);
                log.info("Result: {}", result);
            } catch (Exception e) {
                log.error("Error occurred while processing user.username: {}", username, e);
                thrown = e;
            } finally {
                account.unlock();
                componentMetrics.put(this.getClass(), "account_instance", System.currentTimeMillis() - t, thrown);
            }

        });
    }

}
