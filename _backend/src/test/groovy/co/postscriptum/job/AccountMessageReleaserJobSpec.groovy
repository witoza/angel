package co.postscriptum.job

import co.postscriptum.db.Account
import co.postscriptum.db.DB
import co.postscriptum.email.UserEmailService
import co.postscriptum.internal.ReleasedMessagesDetails
import co.postscriptum.metrics.ComponentMetrics
import co.postscriptum.model.bo.DataFactory
import co.postscriptum.model.bo.Lang
import co.postscriptum.model.bo.RequiredAction
import co.postscriptum.model.bo.Trigger
import co.postscriptum.model.bo.User
import co.postscriptum.model.bo.UserData
import co.postscriptum.model.bo.UserInternal
import co.postscriptum.security.RSAOAEPEncrypted
import co.postscriptum.service.AdminHelperService
import co.postscriptum.service.MessageReleaseService
import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Subject

import java.time.temporal.ChronoUnit
import java.util.function.Consumer

@Slf4j
class AccountMessageReleaserJobSpec extends Specification {

    @Subject
    AccountMessageReleaserJob job

    def setup() {
        job = new AccountMessageReleaserJob()
        job.componentMetrics = Mock(ComponentMetrics)
        job.db = Mock(DB)

        job.db.withLoadedAccount(_ as Account, _ as Consumer<Account>) >> { arguments ->
            log.info("with loaded account...")
            (arguments[1] as Consumer<Account>).accept(arguments[0] as Account)
        }

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
        e.message == "Account must be unloaded"
    }

    def 'admin account'() {
        when:
        UserData userData = DataFactory.newUserData(
                User.builder()
                    .role(User.Role.admin)
                    .build(),
                new UserInternal())

        process(userData)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Admin user not allowed"
    }

    def 'should require manual release'() {
        given:
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
                                        .stage(Trigger.Stage.BEFORE_X)
                                        .build())
                        .build()

        UserData userData = DataFactory.newUserData(user, new UserInternal())
        userData.getInternal().setEncryptionKeyEncryptedByAdminPublicKey(new RSAOAEPEncrypted())

        when:
        def result = process(userData)

        then:
        result == "Not active"

        when:
        user.setActive(true)
        result = process(userData)

        then:
        result == "TOS not accepted"

        when:
        user.setTosAccepted(true)
        result = process(userData)

        then:
        result == "Account never logged to"

        when:
        user.setLastAccess(System.currentTimeMillis())
        result = process(userData)

        then:
        result == "Trigger is disabled"

        when:
        user.getTrigger().setEnabled(true)
        result = process(userData)

        then:
        result == "Still BEFORE_X"
        user.getTrigger().stage == Trigger.Stage.BEFORE_X

        when:
        user.setLastAccess(System.currentTimeMillis() - millis(user.getTrigger().getX(), 1))
        result = process(userData)

        then:
        result == "AFTER_X_BEFORE_Y activated"
        user.getTrigger().stage == Trigger.Stage.AFTER_X_BEFORE_Y
        1 * job.userEmailService.sendUserVerificationAfterX(userData, false)

        when:
        result = process(userData)

        then:
        result == "Still AFTER_X_BEFORE_Y"
        user.getTrigger().stage == Trigger.Stage.AFTER_X_BEFORE_Y

        when:
        user.setLastAccess(System.currentTimeMillis() - millis(user.getTrigger().getX() + user.getTrigger().getY(), 1))
        result = process(userData)

        then:
        result == "AFTER_Y_BEFORE_Z activated"
        user.getTrigger().stage == Trigger.Stage.AFTER_Y_BEFORE_Z
        1 * job.userEmailService.sendUserVerificationAfterY(userData, false)

        when:
        result = process(userData)

        then:
        result == "Still AFTER_Y_BEFORE_Z"
        user.getTrigger().stage == Trigger.Stage.AFTER_Y_BEFORE_Z

        when:
        user.setLastAccess(System.currentTimeMillis() - millis(user.getTrigger().getX() + user.getTrigger().getY() + user.getTrigger().getZ(), 1))
        result = process(userData)

        then:
        result == "AFTER_Z_BEFORE_RELEASE activated"
        user.getTrigger().stage == Trigger.Stage.AFTER_Z_BEFORE_RELEASE
        1 * job.userEmailService.sendUserVerificationAfterZ(userData, false)

        when:
        result = process(userData)

        then:
        result == "Still AFTER_Z_BEFORE_RELEASE"
        user.getTrigger().stage == Trigger.Stage.AFTER_Z_BEFORE_RELEASE

        when:
        user.setLastAccess(System.currentTimeMillis() - millis(user.getTrigger().getX() + user.getTrigger().getY() + user.getTrigger().getZ() + user.getTrigger().getW(), 1))
        result = process(userData)

        then:
        result == "RELEASED activated"
        user.getTrigger().releasedTime == 0

        user.getTrigger().stage == Trigger.Stage.RELEASED
        1 * job.userEmailService.sendToOwnerMessagesAreAboutToBeReleased(userData)
        1 * job.adminHelperService.addAdminRequiredAction({ requiredAction ->
            requiredAction.type == RequiredAction.Type.REQUIRE_MANUAL_RELEASE_MESSAGES
                                                          } as RequiredAction)
    }

    def 'should release messages automatically'() {
        given:
        User user = User.builder()
                        .role(User.Role.user)
                        .active(true)
                        .lastAccess(System.currentTimeMillis())
                        .tosAccepted(true)
                        .trigger(Trigger.builder()
                                        .enabled(true)
                                        .timeUnit(ChronoUnit.MINUTES)
                                        .x(5)
                                        .y(10)
                                        .z(15)
                                        .w(20)
                                        .stage(Trigger.Stage.BEFORE_X)
                                        .build())
                        .build()
        UserData userData = DataFactory.newUserData(user, new UserInternal())
        userData.getInternal().setLang(Lang.pl)
        def result
        ReleasedMessagesDetails releasedMessagesDetails = new ReleasedMessagesDetails()

        when:
        user.setLastAccess(System.currentTimeMillis() - millis(user.getTrigger().getX() + user.getTrigger().getY() + user.getTrigger().getZ() + user.getTrigger().getW(), 1))
        result = process(userData)
        then:
        result == "AFTER_X_BEFORE_Y activated"
        user.getTrigger().stage == Trigger.Stage.AFTER_X_BEFORE_Y
        1 * job.userEmailService.sendUserVerificationAfterX(userData, false)

        when:
        result = process(userData)
        then:
        result == "AFTER_Y_BEFORE_Z activated"
        user.getTrigger().stage == Trigger.Stage.AFTER_Y_BEFORE_Z
        1 * job.userEmailService.sendUserVerificationAfterY(userData, false)

        when:
        result = process(userData)
        then:
        result == "AFTER_Z_BEFORE_RELEASE activated"
        user.getTrigger().stage == Trigger.Stage.AFTER_Z_BEFORE_RELEASE
        1 * job.userEmailService.sendUserVerificationAfterZ(userData, false)

        when:
        result = process(userData)
        then:
        result == "RELEASED activated"
        user.getTrigger().stage == Trigger.Stage.RELEASED
        user.getTrigger().releasedTime > 0
        1 * job.userEmailService.sendToOwnerMessagesAreAboutToBeReleased(userData)
        1 * job.adminHelperService.addAdminRequiredAction({ requiredAction ->
            requiredAction.type == RequiredAction.Type.AUTOMATIC_RELEASE_MESSAGES_HAS_BEEN_DONE
                                                          } as RequiredAction)
        1 * job.messageReleaseService.releaseMessages(userData, Optional.empty()) >> releasedMessagesDetails
        1 * job.messageReleaseService.toHumanReadable(userData.getInternal().getLang(), releasedMessagesDetails) >> "HumanReadable"
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
