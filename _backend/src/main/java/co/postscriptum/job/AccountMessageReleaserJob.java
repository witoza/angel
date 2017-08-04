package co.postscriptum.job;

import co.postscriptum.db.Account;
import co.postscriptum.email.UserEmailService;
import co.postscriptum.exception.InternalException;
import co.postscriptum.internal.ReleasedMessagesDetails;
import co.postscriptum.model.bo.DataFactory;
import co.postscriptum.model.bo.RequiredAction;
import co.postscriptum.model.bo.RequiredAction.Type;
import co.postscriptum.model.bo.Trigger;
import co.postscriptum.model.bo.Trigger.Stage;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.service.AdminHelperService;
import co.postscriptum.service.MessageReleaseService;
import co.postscriptum.service.UserDataHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@Slf4j
public class AccountMessageReleaserJob extends AbstractAccountJob {

    public static final String USER_LAST_ACCESS = "userLastAccess";

    @Autowired
    MessageReleaseService messageReleaseService;

    @Autowired
    AdminHelperService adminHelperService;

    @Autowired
    UserEmailService userEmailService;

    @Override
    public Stream<Account> getAccountsToTest() {
        return db.getUserUnloadedAccounts();
    }

    @Scheduled(fixedDelay = 60000)
    @Override
    public void process() {
        super.process();
    }

    @Override
    public String processAccount(Account account) {

        account.assertLockIsHeldByCurrentThread();

        if (account.isLoaded()) {
            throw new IllegalStateException("Account must be unloaded");
        }

        User user = account.getUserData().getUser();

        if (user.getRole() != User.Role.user) {
            throw new IllegalStateException("Admin user not allowed");
        }

        if (!user.isActive()) {
            return "Not active";
        }
        if (!user.isTosAccepted()) {
            return "TOS not accepted";
        }
        if (user.getLastAccess() == 0) {
            return "Account never logged to";
        }

        Trigger trigger = user.getTrigger();
        if (!trigger.getEnabled()) {
            return "Trigger is disabled";
        }

        if (trigger.getStage() == Stage.RELEASED) {
            throw new InternalException("Messages has already been released but trigger is still enabled");
        }

        TimeStages timeStages = new TimeStages(trigger, LocalDateTime.now(), user.getLastAccess());

        log.info("Verifying user trigger, debugData: {}", timeStages.debug());

        Stage nextStage = timeStages.nextStage(trigger.getStage());
        if (nextStage == trigger.getStage()) {
            return "Still " + trigger.getStage();
        }

        activateStage(account, nextStage, timeStages);
        log.info("Stage {} has been activated", nextStage);
        return nextStage + " activated";
    }

    private void activateStage(Account acc, Stage stage, TimeStages timeStages) {
        log.info("Activating stage {}", stage);

        db.withLoadedAccount(acc, account -> {
            UserData userData = account.getUserData();

            if (stage == Stage.BEFORE_X) {
                // nothing to activate

            } else if (stage == Stage.AFTER_X_BEFORE_Y) {

                userEmailService.sendUserVerificationAfterX(userData, false);

            } else if (stage == Stage.AFTER_Y_BEFORE_Z) {

                userEmailService.sendUserVerificationAfterY(userData, false);

            } else if (stage == Stage.AFTER_Z_BEFORE_RELEASE) {

                userEmailService.sendUserVerificationAfterZ(userData, false);

            } else if (stage == Stage.RELEASED) {

                ReleasedMessagesDetails details = releaseMessages(userData);

                Trigger trigger = userData.getUser().getTrigger();

                Map<String, Object> raDetails = new HashMap<>();
                raDetails.put(USER_LAST_ACCESS, String.valueOf(userData.getUser().getLastAccess()));
                raDetails.put("timeStages", timeStages.debug());
                raDetails.put("trigger", trigger);

                RequiredAction.Type type;
                if (details != null) {
                    raDetails.put("status", details.getDetails());
                    type = Type.AUTOMATIC_RELEASE_MESSAGES_HAS_BEEN_DONE;
                } else {
                    type = Type.REQUIRE_MANUAL_RELEASE_MESSAGES;
                }

                adminHelperService.addAdminRequiredAction(DataFactory.newRequiredAction(userData, type, raDetails));

                trigger.setEnabled(false);

            } else {
                throw new IllegalArgumentException("Don't know how to activate stage " + stage);
            }

            userData.getUser().getTrigger().setStage(stage);
        });

        try {
            db.unloadAccount(acc);
        } catch (Exception e) {
            log.error("error while unloading account");
        }

    }

    private ReleasedMessagesDetails releaseMessages(UserData userData) {

        userEmailService.sendToOwnerMessagesAreAboutToBeReleased(userData);

        if (userData.getInternal().getEncryptionKeyEncryptedByAdminPublicKey() != null) {
            log.info("Message release must be done by Admin");
            return null;
        }

        log.info("About to message release");

        ReleasedMessagesDetails details = messageReleaseService.releaseMessages(userData, Optional.empty());

        messageReleaseService.sendToOwnerReleasedMessageSummary(userData, details);

        new UserDataHelper(userData).addNotification(
                "User messages have been released:\n" + messageReleaseService.toHumanReadable(
                        userData.getInternal().getLang(), details));

        userData.getUser().getTrigger().setReleasedTime(System.currentTimeMillis());

        return details;
    }

}
