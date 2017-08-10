package co.postscriptum.service;

import co.postscriptum.exception.BadRequestException;
import co.postscriptum.fs.FS;
import co.postscriptum.internal.InfoInputStream;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.BO2DTOConverter;
import co.postscriptum.model.bo.DataFactory;
import co.postscriptum.model.bo.File;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.PasswordEncryption;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.dto.FileDTO;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class FileService {

    private final FS fs;

    public InputStream getFileContent(@NonNull UserData userData,
                                      @NonNull File file,
                                      @NonNull SecretKey encryptionKey,
                                      String encryptionPassword) throws IOException {

        InputStream fis = fs.load(filePath(file, userData.getUser()), true);

        if (file.getEncryption() != null) {
            log.info("File is password encrypted");

            if (encryptionPassword == null) {
                throw new BadRequestException("EncryptionPassword is required to decrypt file");
            }

            SecretKey passwordKey = AESKeyUtils.deriveKey(encryptionPassword, file.getEncryption().getSalt());

            InputStream dataStream = AESGCMUtils.decryptedStream(fis, passwordKey, file.getEncryption().getIv(), new String[]{});

            return AESGCMUtils.decryptedStream(dataStream,
                                               encryptionKey,
                                               file.getIv(),
                                               new String[]{file.getOriginalFileUuid()});

        } else {
            log.info("File is not password encrypted");

            return AESGCMUtils.decryptedStream(fis, encryptionKey, file.getIv(), new String[]{file.getUuid()});
        }

    }

    public File upload(UserData userData, SecretKey userEncryptionKey, MultipartFile uploadingFile, String title) throws IOException {

        if (userData.getFiles().size() > 100) {
            throw new BadRequestException("Too many files");
        }

        String fileName = uploadingFile.getOriginalFilename();
        if (StringUtils.isNotBlank(title)) {
            fileName = title;
        }

        if (uploadingFile.getSize() > 0) {
            userData.verifyQuotaNotExceeded(uploadingFile.getSize());
        }

        File file = File.builder()
                        .uploadTime(System.currentTimeMillis())
                        .name(fileName)
                        .uuid(Utils.randKey("F"))
                        .build();

        if (uploadingFile.getContentType().equals("video/webm")) {
            // uploaded recordings are 'webm'
            file.setExt("webm");
        } else {
            String extension = FilenameUtils.getExtension(file.getName());
            if (StringUtils.isEmpty(extension)) {
                log.info("Could not determine file extension from file name: {}", file.getName());
            } else {
                file.setExt(extension);
            }
        }

        file.setMime(uploadingFile.getContentType());

        saveStreamToFile(file, userData.getUser(), userEncryptionKey, uploadingFile.getInputStream());

        try {
            userData.verifyQuotaNotExceeded(file.getSize());
        } catch (Exception e) {
            fs.remove(filePath(userData, file));
            throw e;
        }

        userData.getFiles().add(file);

        log.info("File has been uploaded name: {}, uuid: {}", file.getName(), file.getUuid());

        return file;
    }

    private void saveStreamToFile(@NonNull File file,
                                  @NonNull User owner,
                                  @NonNull SecretKey encryptionKey,
                                  @NonNull InputStream input) throws IOException {

        OutputStream saveTo = fs.saveTo(filePath(file, owner));

        try (InfoInputStream fis = new InfoInputStream(input)) {

            file.setIv(AESGCMUtils.encryptStream(encryptionKey, fis, saveTo, new String[]{file.getUuid()}));
            file.setSha1(fis.getSha1());
            file.setSize(fis.getSize());

        } finally {
            saveTo.close();
        }
    }

    private String filePath(File file, User owner) {
        return owner.getUuid() + "/" + file.getUuid();
    }

    private String filePath(UserData userData, File file) {
        return filePath(file, userData.getUser());
    }

    public void deleteFile(UserData userData, String fileUuid) throws IOException {
        deleteFile(userData, userData.requireFileByUuid(fileUuid));
    }

    private void deleteFile(UserData userData, File file) throws IOException {

        log.info("Deleting file name: {}, uuid: {}", file.getName(), file.getUuid());

        fs.remove(filePath(userData, file));

        userData.getMessages().forEach(m -> m.getAttachments().removeIf(att -> att.equals(file.getUuid())));
        userData.getFiles().removeIf(f -> f.getUuid().equals(file.getUuid()));

    }

    public FileDTO convertToDto(UserData userData, File file) {

        FileDTO fdto = BO2DTOConverter.toFileDTO(userData, file);

        fdto.setMessages(userData.getMessages()
                                 .stream()
                                 .filter(m -> m.getAttachments().contains(file.getUuid()))
                                 .map(BO2DTOConverter::toMessageDTO)
                                 .collect(Collectors.toList()));

        return fdto;
    }

    public File decrypt(UserData userData, SecretKey userEncryptionKey, String msgUuid, String fileUuid, String password) throws IOException {

        Message message = userData.requireMessageByUuid(msgUuid);

        File encryptedFile = userData.requireFileByUuid(fileUuid);
        if (encryptedFile.getEncryption() == null) {
            throw new BadRequestException("Can't decrypt not encrypted file");
        }

        int attachmentIndex = message.getAttachments().indexOf(encryptedFile.getUuid());
        if (attachmentIndex == -1) {
            throw new BadRequestException("File is not connected to that message");
        }

        Optional<File> originalFileOpt = userData.getFileByUuid(encryptedFile.getOriginalFileUuid());

        File originalFile;
        if (!originalFileOpt.isPresent()) {

            log.info("Original file has been removed, recreating original file based on encrypted one");

            originalFile = DataFactory.cloneBasicData(encryptedFile);
            originalFile.setUuid(encryptedFile.getOriginalFileUuid());

            try (InputStream fileInput = getFileContent(userData, encryptedFile, userEncryptionKey, password)) {
                fs.save(filePath(userData, originalFile), fileInput);
            }

            userData.getFiles().add(originalFile);

        } else {

            log.info("Original file exists - switching references only");

            originalFile = originalFileOpt.get();

        }

        message.getAttachments().set(attachmentIndex, originalFile.getUuid());

        message.setAttachments(Utils.unique(message.getAttachments()));

        deleteFile(userData, encryptedFile);

        return originalFile;
    }

    public File encrypt(UserData userData, String msgUuid, String fileUuid, String password) throws IOException {

        Message message = userData.requireMessageByUuid(msgUuid);

        File file = userData.requireFileByUuid(fileUuid);
        if (file.getEncryption() != null) {
            throw new BadRequestException("File is already encrypted");
        }
        userData.verifyQuotaNotExceeded(file.getSize());

        int attachmentIndex = message.getAttachments().indexOf(file.getUuid());
        if (attachmentIndex == -1) {
            throw new BadRequestException("File is not connected to that message");
        }
        log.info("Message attachment index: {}", attachmentIndex);

        File encryptedFile = encryptFileByPassword(file, userData.getUser(), password);

        // this file is only attached to this message, it belongs to that message
        encryptedFile.setBelongsTo(message.getUuid());

        userData.getFiles().add(encryptedFile);

        message.getAttachments().set(attachmentIndex, encryptedFile.getUuid());

        return encryptedFile;
    }

    private File encryptFileByPassword(@NonNull File file,
                                       @NonNull User owner,
                                       @NonNull String encryptionPassword) throws IOException {

        log.info("Encrypting file uuid: {} by password", file.getUuid());

        File encryptedFile = DataFactory.cloneBasicData(file);
        encryptedFile.setUuid(Utils.randKey("EF"));
        encryptedFile.setOriginalFileUuid(file.getUuid());

        OutputStream saveTo = fs.saveTo(filePath(encryptedFile, owner));

        byte[] salt = AESKeyUtils.randomSalt();

        SecretKey passwordKey = AESKeyUtils.deriveKey(encryptionPassword, salt);

        try (InputStream fis = fs.load(filePath(file, owner), true)) {

            byte[] iv = AESGCMUtils.encryptStream(passwordKey, fis, saveTo, new String[]{});

            encryptedFile.setEncryption(PasswordEncryption.builder()
                                                          .salt(salt)
                                                          .iv(iv)
                                                          .build());

        } finally {
            saveTo.close();
        }

        return encryptedFile;
    }

}
