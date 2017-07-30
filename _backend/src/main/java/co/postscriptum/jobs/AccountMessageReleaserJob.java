package co.postscriptum.jobs;

import co.postscriptum.db.Account;
import co.postscriptum.email.UserEmailService;
import co.postscriptum.exception.InternalException;
import co.postscriptum.internal.AdminHelperService;
import co.postscriptum.internal.ReleasedMessagesDetails;
import co.postscriptum.model.bo.DataFactory;
import co.postscriptum.model.bo.RequiredAction;
import co.postscriptum.model.bo.RequiredAction.Type;
import co.postscriptum.model.bo.Trigger;
import co.postscriptum.model.bo.Trigger.Stage;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.service.MessageReleaseService;
import co.postscriptum.service.UserDataHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.stream.Stream;

@Component
@Slf4j
public class AccountMessageReleaserJob extends AbstractAccountJob {

    public static final String userLastAccess = "userLastAccess";

    @Autowired
    private MessageReleaseService messageReleaseService;

    @Autowired
    private AdminHelperService adminHelperService;

    @Autowired
    private UserEmailService userEmailService;

    @Override
    public String processAccount(Account account) {

        account.assertLockIsHeldByCurrentThread();

        if (account.isLoaded()) {
            throw new IllegalStateException("account must be unloaded");
        }

        User user = account.getUserData().getUser();

        if (user.getRole() != User.Role.user) {
            throw new IllegalStateException("admin user not allowed");
        }

        if (!user.isActive()) {
            return "not active";
        }
        if (!user.isTosAccepted()) {
            return "tos not accepted";
        }
        if (user.getLastAccess() == 0) {
            return "account never logged to";
        }

        Trigger trigger = user.getTrigger();
        if (!trigger.getEnabled()) {
            return "trigger is disabled";
        }

        if (trigger.getStage() == Stage.released) {
            throw new InternalException("messages has already been released but trigger is still enabled");
        }

        TimeStages timeStages = new TimeStages(trigger, user.getLastAccess());

        log.info("verifying user trigger, debugData: {}", timeStages.debug());

        Stage nextStage = timeStages.nextStage(trigger.getStage());

        if (nextStage == trigger.getStage()) {
            return "still " + trigger.getStage();
        } else {
            activate(account, nextStage, timeStages);

            log.info("stage {} has been activated", nextStage);

            return nextStage + " activated";
        }
    }

    private UserData loadUserData(Account account) {

        db.loadAccount(account);

        return account.getUserData();
    }

    private void activate(Account account, Stage stage, TimeStages timeStages) {

        log.info("activating stage {}", stage);

        UserData userData = loadUserData(account);

        if (stage == Stage.beforeX) {
            // nothing to activate

        } else if (stage == Stage.afterXbeforeY) {

            userEmailService.sendTriggerAfterX(userData, false);

        } else if (stage == Stage.afterYbeforeZ) {

            userEmailService.sendTriggerAfterY(userData, false);

        } else if (stage == Stage.afterZbeforeW) {

            userEmailService.sendTriggerAfterZ(userData, false);

        } else if (stage == Stage.released) {

            activateRelease(userData, timeStages);

        } else {
            throw new IllegalArgumentException("can't activate stage " + stage);
        }

        userData.getUser().getTrigger().setStage(stage);

    }

    private void activateRelease(UserData userData, TimeStages timeStages) {

        userEmailService.sendUserMessagesAreAboutToBeReleased(userData);

        Trigger trigger = userData.getUser().getTrigger();

        trigger.setEnabled(false);
        trigger.setReadyToBeReleasedTime(System.currentTimeMillis());

        RequiredAction ra = DataFactory.newRequiredAction(userData, null);
        ra.getDetails().put(userLastAccess, "" + userData.getUser().getLastAccess());
        ra.getDetails().put("timeStages", timeStages.debug());
        ra.getDetails().put("trigger", trigger);

        if (userData.getInternal().getEncryptionKeyEncryptedByAdminPublicKey() == null) {

            ra.setType(Type.automatic_release_messages);

            ReleasedMessagesDetails details = messageReleaseService.releaseMessages(userData, Optional.empty());

            messageReleaseService.sendUserReleasedMessageSummary(userData, details);

            ra.getDetails().put("status", details.getDetails());

            new UserDataHelper(userData).addNotification(
                    "User messages have been released:\n" + messageReleaseService.toHumanReadable(
                            userData.getInternal().getLang(), details));

            trigger.setHaveBeenReleasedTime(System.currentTimeMillis());
        } else {

            ra.setType(Type.manual_release_messages);

        }

        adminHelperService.addAdminRequiredAction(ra);

    }

    @Override
    public Stream<Account> getAccountsToTest() {
        return db.getUserUnloadedAccounts();
    }

    @Scheduled(fixedDelay = 60000)
    @Override
    public void process() {
        super.process();
    }
}
