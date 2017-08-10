package co.postscriptum.job

import co.postscriptum.db.Account
import co.postscriptum.db.DB
import co.postscriptum.metrics.ComponentMetrics
import co.postscriptum.model.bo.DataFactory
import co.postscriptum.model.bo.Message
import co.postscriptum.model.bo.ReleaseItem
import co.postscriptum.model.bo.RequiredAction
import co.postscriptum.model.bo.Trigger
import co.postscriptum.model.bo.User
import co.postscriptum.model.bo.UserData
import co.postscriptum.service.AdminHelperService
import spock.lang.Specification
import spock.lang.Subject

class AccountRemoverJobSpec extends Specification {

    @Subject
    AccountRemoverJob job

    def setup() {
        job = new AccountRemoverJob()
        job.componentMetrics = Mock(ComponentMetrics)
        job.db = Mock(DB)
        job.adminHelperService = Mock(AdminHelperService)
        job.releaseItemAvailableForAfterOpeningMins = 2
        job.createNewReminderAfterMins = 4
        job.runLogicAfterTriggeringMins = 6
    }

    def 'should not process already marked as to be removed'() {
        given:
        Account account = Mock(Account)
        account.isToBeRemoved() >> true

        expect:
        job.processAccount(account) == 'Account is already marked as to be removed'
    }

    def 'should eventually allow to remove account no messages'() {
        given:
        User user = User.builder()
                        .uuid(UUID.randomUUID().toString())
                        .active(true)
                        .trigger(Trigger.builder()
                                        .stage(Trigger.Stage.BEFORE_X)
                                        .build())
                        .build()
        UserData userData = DataFactory.newUserData(user, null)

        when:
        def result = process(userData)

        then:
        result == 'Account is not in RELEASED state'

        when:
        user.getTrigger().setStage(Trigger.Stage.RELEASED)
        result = process(userData)

        then:
        result == 'Messages haven\'t been released yet'

        when:
        user.getTrigger().setReleasedTime(System.currentTimeMillis())
        result = process(userData)

        then:
        result == 'Account has been released less than ' + job.runLogicAfterTriggeringMins + ' minutes ago'

        when:
        user.getTrigger().setReleasedTime(System.currentTimeMillis() - millis(job.runLogicAfterTriggeringMins, 1))
        result = process(userData)

        then:
        result == 'Account can be removed, all reachable release items are not available'
        !user.isActive()
        1 * job.adminHelperService.addAdminRequiredAction({ requiredAction ->
            assert requiredAction.userUuid == userData.getUser().getUuid()
            assert requiredAction.type == RequiredAction.Type.ACCOUNT_CAN_BE_REMOVED
            requiredAction
                                                          } as RequiredAction)
    }

    def 'should eventually allow to remove account when messages has been opened'() {
        given:
        User user = User.builder()
                        .uuid(UUID.randomUUID().toString())
                        .active(true)
                        .trigger(Trigger.builder()
                                        .stage(Trigger.Stage.RELEASED)
                                        .releasedTime(System.currentTimeMillis() - millis(job.runLogicAfterTriggeringMins, 1))
                                        .build())
                        .build()
        UserData userData = DataFactory.newUserData(user, null)

        Message message1 = DataFactory.newMessage()
        ReleaseItem releaseItem1 = message1.addReleaseItem("recipient1@postscriptum.co")

        Message message2 = DataFactory.newMessage()
        ReleaseItem releaseItem2 = message2.addReleaseItem("recipient2@postscriptum.co")
        releaseItem2.setFirstTimeAccess(System.currentTimeMillis())

        Message message3 = DataFactory.newMessage()
        ReleaseItem releaseItem3 = message3.addReleaseItem("recipient3@postscriptum.co")

        userData.getMessages().addAll([message1, message2, message3])

        when:
        def result = process(userData)

        then:
        result == 'There are 2 messages that needs admin\'s attention (reminders has been created)'
        releaseItem1.reminders.size() == 1
        releaseItem2.reminders.size() == 0
        releaseItem3.reminders.size() == 1
        2 * job.adminHelperService.addAdminRequiredAction({ requiredAction ->
            assert requiredAction.userUuid == user.getUuid()
            assert requiredAction.type == RequiredAction.Type.MESSAGE_NOT_YET_OPENED_BY_THE_RECIPIENT
            requiredAction
                                                          } as RequiredAction)

        when:
        result = process(userData)

        then:
        result == 'Some release items can still be reachable, waiting for admin to verify'

        when: /resolve Message1 Reminder now/
        releaseItem1.reminders[0].setResolved(true)
        releaseItem1.reminders[0].setInput("recipient contacted")
        releaseItem1.reminders[0].setResolvedTime(System.currentTimeMillis())
        result = process(userData)

        then:
        result == 'Some release items can still be reachable, waiting for admin to verify'

        when: /resolve Reminder in the past/
        releaseItem1.reminders[0].setResolvedTime(System.currentTimeMillis() - millis(job.createNewReminderAfterMins, 1))
        result = process(userData)

        then:
        result == 'There are 1 messages that needs admin\'s attention (reminders has been created)'
        releaseItem1.reminders.size() == 2
        releaseItem2.reminders.size() == 0
        releaseItem3.reminders.size() == 1
        1 * job.adminHelperService.addAdminRequiredAction({ requiredAction ->
            assert requiredAction.userUuid == user.getUuid()
            assert requiredAction.type == RequiredAction.Type.MESSAGE_NOT_YET_OPENED_BY_THE_RECIPIENT
            requiredAction
                                                          } as RequiredAction)

        when: /recipient opened the message now/
        releaseItem1.setFirstTimeAccess(System.currentTimeMillis())
        result = process(userData)

        then:
        result == 'Some release items can still be reachable, waiting for admin to verify'

        when: /recipient1 opened the message in the past/
        releaseItem1.setFirstTimeAccess(System.currentTimeMillis() - millis(job.releaseItemAvailableForAfterOpeningMins, 1))
        result = process(userData)

        then:
        result == 'Some release items can still be reachable, waiting for admin to verify'

        when: /recipient2 opened the message in the past/
        releaseItem2.setFirstTimeAccess(System.currentTimeMillis() - millis(job.releaseItemAvailableForAfterOpeningMins, 1))
        result = process(userData)

        then:
        result == 'Some release items can still be reachable, waiting for admin to verify'

        when: /can't contact recipient3/
        releaseItem3.reminders[0].setResolved(true)
        releaseItem3.reminders[0].setInput(ReleaseItem.CAN_NOT_CONTACT_RECIPIENT)
        releaseItem3.reminders[0].setResolvedTime(System.currentTimeMillis())
        result = process(userData)

        then:
        result == 'Account can be removed, all reachable release items are not available'
        !user.isActive()
        1 * job.adminHelperService.addAdminRequiredAction({ requiredAction ->
            assert requiredAction.userUuid == user.getUuid()
            assert requiredAction.type == RequiredAction.Type.ACCOUNT_CAN_BE_REMOVED
            requiredAction
                                                          } as RequiredAction)
    }

    def millis(int minutes, int seconds) {
        minutes * (60 + seconds) * 1000
    }

    def process(UserData userData) {
        Account account = Mock(Account)
        account.isLoaded() >> false
        account.getUserData() >> userData
        return job.processAccount(account)
    }

}
