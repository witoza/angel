package co.postscriptum.job;

import co.postscriptum.db.Account;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.DataFactory;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.ReleaseItem;
import co.postscriptum.model.bo.ReleaseItem.Reminder;
import co.postscriptum.model.bo.RequiredAction;
import co.postscriptum.model.bo.RequiredAction.Type;
import co.postscriptum.model.bo.Trigger;
import co.postscriptum.model.bo.Trigger.Stage;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.service.AdminHelperService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@Slf4j
public class AccountRemoverJob extends AbstractAccountJob {

    public static final String RELEASE_ITEM_MESSAGE_UUID = "releaseItem.message.uuid";

    public static final String RELEASE_ITEM_KEY = "releaseItem.key";

    public static final String REMAINDER_UUID = "remainder.uuid";

    @Autowired
    private AdminHelperService adminHelperService;

    //10080 min == 7 days
    @Value("${accountRemoverJob.releaseItemAvailableForAfterOpeningMins:10080}")
    private Integer releaseItemAvailableForAfterOpeningMins;

    @Value("${accountRemoverJob.createNewReminderAfterMins:10080}")
    private Integer createNewReminderAfterMins;

    @Value("${accountRemoverJob.runLogicAfterTriggeringMins:20160}")
    private Integer runLogicAfterTriggeringMins;

    @Override
    public Stream<Account> getAccountsToTest() {
        return db.getUserUnloadedAccounts();
    }

    // run every hour
    @Scheduled(fixedDelay = 60 * 3600 * 1000)
    @Override
    public void process() {
        super.process();
    }

    @Override
    public String processAccount(Account account) {
        account.assertLockIsHeldByCurrentThread();

        if (account.isToBeRemoved()) {
            return "Account is already marked as to be removed";
        }

        UserData userData = account.getUserData();
        Trigger trigger = userData.getUser().getTrigger();

        if (trigger.getStage() != Stage.RELEASED) {
            return "Account is not in RELEASED state";
        }

        if (trigger.getReleasedTime() == 0) {
            return "Messages haven't been released yet";
        }

        if (lessThanXMinutesAgo(trigger.getReleasedTime(), runLogicAfterTriggeringMins)) {
            return "Account has been released less than " + runLogicAfterTriggeringMins + " minutes ago";
        }

        log.info("Verifying if account can be removed");

        // after runLogicAfterTriggeringMins minutes from the triggering, we try to do the cleanup if necessary we will send admin event that msg has not been opened

        db.loadAccount(account);

        long newRemindersCreated = createReminders(userData);
        if (newRemindersCreated > 0) {
            return "There are " + newRemindersCreated + " messages that needs admin's attention (reminders has been created)";
        }

        boolean accountCanBeRemoved = userData.getMessages().stream().noneMatch(this::shouldMessageStillBeAvailable);
        if (accountCanBeRemoved) {

            userData.getUser().setActive(false);
            account.setToBeRemoved(true);

            adminHelperService.addAdminRequiredAction(DataFactory.newRequiredAction(userData, Type.ACCOUNT_CAN_BE_REMOVED));

            return "Account can be removed, all reachable release items are not available";
        }

        return "Some release items can still be reachable, waiting for admin to verify";
    }

    private boolean lessThanXMinutesAgo(long timestamp, long minutes) {
        return LocalDateTime.now().isBefore(Utils.fromTimestamp(timestamp).plusMinutes(minutes));
    }

    private boolean shouldNewReminderBeCreated(Optional<Reminder> lastReminder) {

        if (!lastReminder.isPresent()) {
            return true;
        }

        Reminder reminder = lastReminder.get();

        if (!reminder.isResolved()) {
            // if the reminder is not resolved, don't create new ones
            return false;
        }

        // create new Reminder every so often
        return !lessThanXMinutesAgo(reminder.getResolvedTime(), createNewReminderAfterMins);
    }

    private boolean shouldNewReminderBeCreated(ReleaseItem releaseItem) {
        if (releaseItem.getFirstTimeAccess() > 0) {
            // message has been opened, no reminder needed
            return false;
        }

        if (!releaseItem.adminAbleToContactRecipient()) {
            // recipient is not reachable, no reminder needed
            return false;
        }

        return shouldNewReminderBeCreated(Utils.getLast(releaseItem.getReminders()));
    }

    private Stream<ReleaseItem> requireNewReminder(Message message) {
        return message.getRelease().getItems().stream().filter(this::shouldNewReminderBeCreated);
    }

    private Stream<Pair<Message, ReleaseItem>> requireNewReminder(UserData userData) {
        return userData.getMessages().stream()
                       .flatMap(message -> requireNewReminder(message).map(releaseItem -> Pair.of(message, releaseItem)));
    }

    private long createReminders(UserData userData) {

        return requireNewReminder(userData).peek(pair -> {

            Message message = pair.getKey();
            ReleaseItem releaseItem = pair.getValue();

            Reminder remainder = DataFactory.newReminder();
            releaseItem.getReminders().add(remainder);

            log.info("Creating new Remainder: {}", remainder);

            RequiredAction ra = DataFactory.newRequiredAction(userData, Type.MESSAGE_NOT_YET_OPENED_BY_THE_RECIPIENT);
            ra.getDetails().put(REMAINDER_UUID, remainder.getUuid());
            ra.getDetails().put(RELEASE_ITEM_MESSAGE_UUID, message.getUuid());
            ra.getDetails().put(RELEASE_ITEM_KEY, releaseItem.getKey());
            ra.getDetails().put("releaseItem.message.title", message.getTitle());
            ra.getDetails().put("releaseItem.recipient", releaseItem.getRecipient());

            adminHelperService.addAdminRequiredAction(ra);

        }).count();

    }

    private boolean releaseItemIsReachableOrShouldBeAvailable(ReleaseItem releaseItem) {
        if (releaseItem.getFirstTimeAccess() == 0) {
            return releaseItem.adminAbleToContactRecipient();
        }
        // message has been opened
        return lessThanXMinutesAgo(releaseItem.getFirstTimeAccess(), releaseItemAvailableForAfterOpeningMins);
    }

    private boolean shouldMessageStillBeAvailable(Message message) {
        return message.getRelease().getItems().stream().anyMatch(this::releaseItemIsReachableOrShouldBeAvailable);
    }

}
