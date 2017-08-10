package co.postscriptum.service

import co.postscriptum.email.Envelope
import co.postscriptum.email.EnvelopeType
import co.postscriptum.internal.ReleasedMessagesDetails
import co.postscriptum.job.EmailProcessor
import co.postscriptum.model.bo.DataFactory
import co.postscriptum.model.bo.Lang
import co.postscriptum.model.bo.Message
import co.postscriptum.model.bo.Trigger
import co.postscriptum.model.bo.User
import co.postscriptum.model.bo.UserData
import co.postscriptum.model.bo.UserInternal
import co.postscriptum.security.AESKeyUtils
import spock.lang.Specification

import java.time.temporal.ChronoUnit

class MessageReleaseServiceSpec extends Specification {

    I18N i18n = Mock(I18N)

    EmailProcessor emailProcessor = Mock(EmailProcessor)

    MessageReleaseService messageReleaseService

    def setup() {
        i18n.translate(Lang.en, _ as String) >> { arguments ->
            return "i18nd(" + arguments[1] + ")"
        }

        i18n.translate(Lang.en, _ as String, _ as Map) >> { arguments ->
            return "i18nd(" + arguments[1] + ")"
        }

        EnvelopeCreatorService envelopeCreatorService = new EnvelopeCreatorService()
        envelopeCreatorService.setI18n(i18n)
        envelopeCreatorService.setDispatcherPrefix("dispatcherPrefix")
        envelopeCreatorService.setHostUrl("hostUrl")

        messageReleaseService = new MessageReleaseService(envelopeCreatorService, i18n, emailProcessor)
    }

    def 'should send out outbox messages with correct recipients'() {
        given:
        User user = User.builder()
                        .uuid(UUID.randomUUID().toString())
                        .username("user@dog.com")
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
        userData.getInternal().setLang(Lang.en)

        when: /empty outbox/
        def details = messageReleaseService.releaseMessages(userData, Optional.empty())

        then:
        details.getDetails().isEmpty()

        when: /with outbox message, no recipient/
        Message message = DataFactory.newMessage()
        message.setType(Message.Type.outbox)
        message.setTitle("title")
        message.setRecipients([])
        userData.getMessages().add(message)
        details = messageReleaseService.releaseMessages(userData, Optional.empty())

        then:
        validateReleasedMessagesDetails(details, message.getTitle(), [], [])

        when: /invalid recipient/
        message.setRecipients(["bademail@@dd"])
        details = messageReleaseService.releaseMessages(userData, Optional.empty())

        then:
        validateReleasedMessagesDetails(details, message.getTitle(), [], ['bademail@@dd'])

        when: /valid recipient, no EncryptionKey/
        message.setRecipients(["test@test.com"])
        details = messageReleaseService.releaseMessages(userData, Optional.empty())

        then:
        validateReleasedMessagesDetails(details, message.getTitle(), ['test@test.com'], [])

        1 * emailProcessor.enqueue(_ as Envelope)

        when: /valid recipient, with EncryptionKey/
        message.setRecipients(["test@test.com"])
        details = messageReleaseService.releaseMessages(userData, Optional.of(AESKeyUtils.generateRandomKey()))

        then:
        validateReleasedMessagesDetails(details, message.getTitle(), ['test@test.com'], [])
        1 * emailProcessor.enqueue({ envelope ->
            assert envelope.recipient == "test@test.com"
            assert envelope.type == EnvelopeType.RELEASE_ITEM
            envelope
        })
    }

    def 'should toHumanReadable'() {
        given:
        ReleasedMessagesDetails.ReleasedMessage releasedMessage = new ReleasedMessagesDetails.ReleasedMessage("msg1")
        releasedMessage.getSentToRecipients().add("sen@to.pl")
        releasedMessage.getInvalidRecipients().add("invali@email.pl")

        ReleasedMessagesDetails details = new ReleasedMessagesDetails()
        details.add(releasedMessage)

        expect:
        messageReleaseService.toHumanReadable(details, Lang.en) == """i18nd(%sent_messages_summary.message%) 'msg1':
 - sen@to.pl: i18nd(%sent_messages_summary.message_about_to_send%)
 - invali@email.pl: i18nd(%sent_messages_summary.message_cant_sent%)"""
    }

    def validateReleasedMessagesDetails(ReleasedMessagesDetails details, String messageTitle, List<String> sentToRecipients, List<String> invalidRecipients) {
        assert details.getDetails().size() == 1
        assert details.getDetails()[0].getMessageTitle() == messageTitle
        assert details.getDetails()[0].getSentToRecipients() == sentToRecipients
        assert details.getDetails()[0].getInvalidRecipients() == invalidRecipients
        true
    }

}
