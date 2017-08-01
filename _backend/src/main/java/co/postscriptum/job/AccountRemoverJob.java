package co.postscriptum.job;

import co.postscriptum.db.Account;
import co.postscriptum.service.AdminHelperService;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.stream.Stream;


@Component
@Slf4j
public class AccountRemoverJob extends AbstractAccountJob {

    public static final String releaseItem_message_uuid = "releaseItem.message.uuid";

    public static final String releaseItem_key = "releaseItem.key";

    public static final String remainder_uuid = "remainder.uuid";

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

    //run every 15 minutes
    @Scheduled(fixedDelay = 900000)
    @Override
    public void process() {
        super.process();
    }

    @Override
    public String processAccount(Account account) {
        account.assertLockIsHeldByCurrentThread();

        if (account.isToBeRemoved()) {
            return "account is already marked as to be removed";
        }

        UserData userData = account.getUserData();
        Trigger trigger = userData.getUser().getTrigger();

        if (trigger.getStage() != Stage.released) {
            return "not yet released";
        }

        if (trigger.getHaveBeenReleasedTime() == 0) {
            return "admin didn't released yet";
        }

        if (lessThanXMinutesAgo(trigger.getHaveBeenReleasedTime(), runLogicAfterTriggeringMins)) {
            return "released less than " + runLogicAfterTriggeringMins + " minutes ago";
        }

        log.info("verifying ...");

        // after runLogicAfterTriggeringMins minutes from the triggering, we try to do the cleanup if necessary we will send admin event that msg has not been opened

        db.loadAccount(account);

        long newRemindersCreated = createReminders(userData);
        if (newRemindersCreated > 0) {
            return "there are " + newRemindersCreated + " messages that needs admin's attention";
        }

        boolean accountCanBeRemoved = userData.getMessages().stream().noneMatch(this::shouldMessageStillBeAvailable);
        if (accountCanBeRemoved) {

            userData.getUser().setActive(false);
            account.setToBeRemoved(true);

            adminHelperService.addAdminRequiredAction(DataFactory.newRequiredAction(userData, Type.account_can_be_removed));

            return "account can be removed, all reachable release items are not available";
        }

        return "some release items can still be reachable, waiting for admin to verify";
    }

    private boolean lessThanXMinutesAgo(long timestamp, long minutes) {
        return LocalDateTime.now().isBefore(Utils.fromTimestamp(timestamp).plusMinutes(minutes));
    }

    private boolean adminAbleToContactRecipient(ReleaseItem releaseItem) {
        Optional<Reminder> lastReminder = Utils.getLast(releaseItem.getReminders());

        if (lastReminder.isPresent()) {
            if (StringUtils.equalsIgnoreCase(lastReminder.get().getInput(), "can_not_contact_recipient")) {
                return false;
            }
        }

        return true;
    }

    private boolean shouldNewReminderBeCreated(Optional<Reminder> lastReminder) {

        if (!lastReminder.isPresent()) {
            return true;
        }

        Reminder reminder = lastReminder.get();

        if (!reminder.isResolved()) {
            //if the reminder is not resolved, don't create new ones
            return false;
        }

        //create new Reminder every so often
        return !lessThanXMinutesAgo(reminder.getResolvedTime(), createNewReminderAfterMins);
    }

    private boolean shouldNewReminderBeCreated(ReleaseItem releaseItem) {
        if (releaseItem.getFirstTimeAccess() > 0) {
            // message has been opened, no reminder needed
            return false;
        }

        if (!adminAbleToContactRecipient(releaseItem)) {
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

            RequiredAction ra = DataFactory.newRequiredAction(userData, Type.message_not_opened_by_the_recipient);
            ra.getDetails().put(remainder_uuid, remainder.getUuid());
            ra.getDetails().put(releaseItem_message_uuid, message.getUuid());
            ra.getDetails().put(releaseItem_key, releaseItem.getKey());
            ra.getDetails().put("releaseItem.message.title", message.getTitle());
            ra.getDetails().put("releaseItem.recipient", releaseItem.getRecipient());

            adminHelperService.addAdminRequiredAction(ra);

        }).count();

    }

    private boolean releaseItemIsReachableOrShouldBeAvailable(ReleaseItem releaseItem) {
        if (releaseItem.getFirstTimeAccess() == 0) {
            return adminAbleToContactRecipient(releaseItem);
        }
        //message has been opened
        return lessThanXMinutesAgo(releaseItem.getFirstTimeAccess(), releaseItemAvailableForAfterOpeningMins);
    }

    private boolean shouldMessageStillBeAvailable(Message message) {
        return message.getRelease().getItems().stream().anyMatch(this::releaseItemIsReachableOrShouldBeAvailable);
    }

}
