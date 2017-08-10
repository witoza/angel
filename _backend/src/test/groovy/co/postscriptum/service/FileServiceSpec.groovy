package co.postscriptum.service

import co.postscriptum.fs.HDFS
import co.postscriptum.model.bo.File
import co.postscriptum.model.bo.Message
import co.postscriptum.test_data.SimpleMultipartFile

class FileServiceSpec extends UserAbstractSpec {

    String testDataDir = "../test_users_files/"

    MessageService messageService = new MessageService()

    FileService fileService

    def setup() {
        HDFS hdfs = new HDFS()
        hdfs.setDbPath("../testdb")
        fileService = new FileService(hdfs)
    }

    def 'should encrypt, delete and decrypt file'() {
        given:
        File uploaded = fileService.upload(userData, encryptionKey, new SimpleMultipartFile(testDataDir, "info_treasure.docx"), null)

        Message message = messageService.addMessage(userData, encryptionKey, Message.Type.outbox, [], [uploaded.getUuid()], "title", "content")

        messageService.setPassword(userData, encryptionKey, message.getUuid(), null, "passwd", "hint")

        when:
        File encrypted = fileService.encrypt(userData, message.getUuid(), uploaded.getUuid(), "knuth")
        fileService.deleteFile(userData, uploaded.getUuid())
        File decrypted = fileService.decrypt(userData, encryptionKey, message.getUuid(), encrypted.getUuid(), "knuth")

        then:
        decrypted.getUuid() == uploaded.getUuid()
        message.getAttachments() == [decrypted.getUuid()]
    }

    def 'should encrypt and decrypt file'() {
        given:
        File uploaded = fileService.upload(userData, encryptionKey, new SimpleMultipartFile(testDataDir, "info_treasure.docx"), null)

        Message message = messageService.addMessage(userData, encryptionKey, Message.Type.outbox, [], [uploaded.getUuid()], "title", "content")

        messageService.setPassword(userData, encryptionKey, message.getUuid(), null, "passwd", "hint")

        when:
        File encrypted = fileService.encrypt(userData, message.getUuid(), uploaded.getUuid(), "knuth")
        File decrypted = fileService.decrypt(userData, encryptionKey, message.getUuid(), encrypted.getUuid(), "knuth")

        then:
        decrypted.getUuid() == uploaded.getUuid()
        message.getAttachments() == [decrypted.getUuid()]
    }

}
