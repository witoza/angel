package co.postscriptum.job

import co.postscriptum.db.Account
import co.postscriptum.db.DB
import co.postscriptum.email.UserEmailService
import co.postscriptum.metrics.ComponentMetrics
import co.postscriptum.model.bo.Trigger
import co.postscriptum.model.bo.User
import co.postscriptum.model.bo.UserData
import co.postscriptum.model.bo.UserInternal
import co.postscriptum.security.RSAOAEPEncrypted
import co.postscriptum.service.AdminHelperService
import co.postscriptum.service.MessageReleaseService
import spock.lang.Specification
import spock.lang.Subject

import java.time.temporal.ChronoUnit

class AccountMessageReleaserJobSpec extends Specification {

    @Subject
    AccountMessageReleaserJob job

    def setup() {
        job = new AccountMessageReleaserJob()
        job.componentMetrics = Mock(ComponentMetrics)
        job.db = Mock(DB)
        job.messageReleaseService = Mock(MessageReleaseService)
        job.adminHelperService = Mock(AdminHelperService)
        job.userEmailService = Mock(UserEmailService)
    }

    def 'account not loaded'() {
        when:
        Account account = Mock(Account)
        account.isLoaded() >> true

        job.processAccount(account)

        then:
        def e = thrown(IllegalStateException)
        e.message == "account must be unloaded"
    }

    def 'admin account'() {
        when:
        User user = User.builder()
                        .role(User.Role.admin)
                        .build()

        process(user)

        then:
        def e = thrown(IllegalStateException)
        e.message == "admin user not allowed"
    }

    def 'should eventually be ready to release'() {
        when:
        User user = User.builder()
                        .role(User.Role.user)
                        .active(false)
                        .lastAccess(0)
                        .tosAccepted(false)
                        .trigger(Trigger.builder()
                                        .enabled(false)
                                        .timeUnit(ChronoUnit.MINUTES)
                                        .x(5)
                                        .y(10)
                                        .z(15)
                                        .w(20)
                                        .stage(Trigger.Stage.beforeX)
                                        .build())
                        .build()

        then:
        process(user) == "not active"

        when:
        user.setActive(true)

        then:
        process(user) == "tos not accepted"

        when:
        user.setTosAccepted(true)

        then:
        process(user) == "account never logged to"

        when:
        user.setLastAccess(System.currentTimeMillis())

        then:
        process(user) == "trigger is disabled"

        when:
        user.getTrigger().setEnabled(true)

        then:
        process(user) == "still beforeX"
        user.getTrigger().stage == Trigger.Stage.beforeX

        then:
        user.setLastAccess(System.currentTimeMillis() - millis(user.getTrigger().getX(), 1))

        then:
        process(user) == "afterXbeforeY activated"
        user.getTrigger().stage == Trigger.Stage.afterXbeforeY

        process(user) == "still afterXbeforeY"
        user.getTrigger().stage == Trigger.Stage.afterXbeforeY

        then:
        user.setLastAccess(System.currentTimeMillis() - millis(user.getTrigger().getX() + user.getTrigger().getY(), 1))

        then:
        process(user) == "afterYbeforeZ activated"
        user.getTrigger().stage == Trigger.Stage.afterYbeforeZ
        process(user) == "still afterYbeforeZ"
        user.getTrigger().stage == Trigger.Stage.afterYbeforeZ

        then:
        user.setLastAccess(System.currentTimeMillis() - millis(user.getTrigger().getX() + user.getTrigger().getY() + user.getTrigger().getZ(), 1))

        then:
        process(user) == "afterZbeforeW activated"
        user.getTrigger().stage == Trigger.Stage.afterZbeforeW
        process(user) == "still afterZbeforeW"
        user.getTrigger().stage == Trigger.Stage.afterZbeforeW

        then:
        user.setLastAccess(System.currentTimeMillis() - millis(user.getTrigger().getX() + user.getTrigger().getY() + user.getTrigger().getZ() + user.getTrigger().getW(), 1))

        then:
        process(user) == "released activated"
        user.getTrigger().stage == Trigger.Stage.released

        1 * job.userEmailService.sendTriggerAfterX(_, false)
        1 * job.userEmailService.sendTriggerAfterY(_, false)
        1 * job.userEmailService.sendTriggerAfterZ(_, false)
        1 * job.userEmailService.sendUserMessagesAreAboutToBeReleased(_)
        1 * job.adminHelperService.addAdminRequiredAction(_)

        user.getTrigger().haveBeenReleasedTime == 0

    }

    def millis(int minutes, int seconds) {
        minutes * (60 + seconds) * 1000
    }

    def process(User user) {

        UserData userData = new UserData()
        userData.setInternal(new UserInternal())
        userData.setUser(user)

        userData.getInternal().setEncryptionKeyEncryptedByAdminPublicKey(new RSAOAEPEncrypted())

        Account account = Mock(Account)
        account.isLoaded() >> false
        account.getUserData() >> userData

        return job.processAccount(account)
    }

}
