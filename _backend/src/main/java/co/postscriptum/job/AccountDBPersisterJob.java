package co.postscriptum.job;

import co.postscriptum.db.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.stream.Stream;

@Slf4j
@Component
public class AccountDBPersisterJob extends AbstractAccountJob {

    @Value("${accountDBPersisterJob.inactivityPeriodSec:60}")
    private Integer inactivityPeriodSec;

    @Override
    public Stream<Account> getAccountsToTest() {
        return db.getLoadedAccounts();
    }

    @Override
    @Scheduled(fixedDelay = 60000)
    protected void process() {
        super.process();
    }

    @Override
    public String processAccount(Account account) throws IOException {

        long notActiveFor = System.currentTimeMillis() - account.getLastAccessTime();
        if (notActiveFor > (inactivityPeriodSec * 1000)) {

            String username = account.getUserData().getUser().getUsername();
            log.info("user {} has not been active for {} ms, unloading it", username, notActiveFor);
            db.unloadAccount(account);

            return "unloaded";
        }

        return "active";
    }

    @Override
    protected void after() throws IOException {
        db.saveStub();
    }

}
