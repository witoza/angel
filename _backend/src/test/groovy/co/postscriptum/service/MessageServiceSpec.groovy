package co.postscriptum.service

import co.postscriptum.model.bo.Message

class MessageServiceSpec extends UserAbstractSpec {

    MessageService messageService = new MessageService()

    def 'should set password for message'() {
        when:
        Message m = messageService.addMessage(userData, encryptionKey, Message.Type.outbox, [], [], "title", "content")

        then:
        m.getContent(encryptionKey, null) == "content"

        when:
        messageService.setPassword(userData, encryptionKey, m.getUuid(), null, "passwd", "hint")

        then:
        m.getContent(encryptionKey, "passwd") == "content"

        when:
        messageService.setPassword(userData, encryptionKey, m.getUuid(), "passwd", "passwd2", "hint")

        then:
        m.getContent(encryptionKey, "passwd2") == "content"

        when:
        messageService.removePassword(userData, encryptionKey, m.getUuid(), "passwd2")

        then:
        m.getContent(encryptionKey, null) == "content"
    }

}
