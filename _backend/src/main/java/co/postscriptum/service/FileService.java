package co.postscriptum.service;

import co.postscriptum.exception.BadRequestException;
import co.postscriptum.exception.InternalException;
import co.postscriptum.fs.FS;
import co.postscriptum.internal.FileEncryptionService;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.BO2DTOConverter;
import co.postscriptum.model.bo.DataFactory;
import co.postscriptum.model.bo.File;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.dto.FileDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class FileService {

    private final FS fs;

    private final FileEncryptionService fileEncryptionService;

    public ResponseEntity<InputStreamResource> openFile(UserData userData, SecretKey userEncryptionKey, String fileUuid) throws IOException {

        File file = new UserDataHelper(userData).requireFileByUuid(fileUuid);

        if (file.getEncryption() != null) {
            throw new BadRequestException("preview not available for password encrypted file");
        }

        log.info("opening file name={}, uuid={}", file.getName(), file.getUuid());

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_TYPE, file.getMime())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .body(new InputStreamResource(decryptedFileStream(file, userData.getUser(), userEncryptionKey, null)));
    }

    public File upload(UserData userData, SecretKey userEncryptionKey, MultipartFile uploadingFile, String title) throws IOException {

        //set some sane limit
        if (userData.getFiles().size() > 100) {
            throw new InternalException("too many files");
        }

        String fileName = uploadingFile.getOriginalFilename();
        if (StringUtils.isNotBlank(title)) {
            fileName = title;
        }

        if (uploadingFile.getSize() > 0) {
            new UserDataHelper(userData).verifyQuotaNotExceeded(uploadingFile.getSize());
        }

        File file = File.builder()
                        .uploadTime(System.currentTimeMillis())
                        .name(fileName)
                        .uuid(Utils.randKey("F"))
                        .build();

        if (uploadingFile.getContentType().equals("video/webm")) {
            //uploaded recordings are 'webm'
            file.setExt("webm");
        } else {
            String extension = FilenameUtils.getExtension(file.getName());
            if (StringUtils.isEmpty(extension)) {
                log.warn("could not determine file extension from file name={}", file.getName());
            } else {
                file.setExt(extension);
            }
        }

        file.setMime(uploadingFile.getContentType());

        fileEncryptionService.saveStreamToFile(file,
                                               userData.getUser(),
                                               userEncryptionKey,
                                               uploadingFile.getInputStream());

        try {
            new UserDataHelper(userData).verifyQuotaNotExceeded(file.getSize());
        } catch (Exception e) {
            fs.remove(filePath(userData, file));
            throw e;
        }

        userData.getFiles().add(file);

        log.info("file has been uploaded name={}, uuid={}", file.getName(), file.getUuid());

        return file;
    }

    private String filePath(File file, User owner) {
        return owner.getUuid() + "/" + file.getUuid();
    }

    private String filePath(UserData userData, File file) {
        return filePath(file, userData.getUser());
    }

    public void deleteFile(UserData userData, String fileUuid) throws IOException {
        deleteFile(userData, new UserDataHelper(userData).requireFileByUuid(fileUuid));
    }

    private void deleteFile(UserData userData, File file) throws IOException {

        log.info("deleting file name={}, uuid={}", file.getName(), file.getUuid());

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

    public void decrypt(UserData userData, SecretKey userEncryptionKey, String msgUuid, String fileUuid, String password) throws IOException {

        Message message = new UserDataHelper(userData).requireMessageByUuid(msgUuid);

        File encryptedFile = new UserDataHelper(userData).requireFileByUuid(fileUuid);
        if (encryptedFile.getEncryption() == null) {
            throw new BadRequestException("can't decrypt not encrypted file");
        }

        int attachmentIndex = message.getAttachments().indexOf(encryptedFile.getUuid());
        if (attachmentIndex == -1) {
            throw new BadRequestException("file is not connected to that message");
        }

        Optional<File> originalFileOpt = new UserDataHelper(userData).getFileByUuid(encryptedFile.getOriginalFileUuid());

        File originalFile;
        if (!originalFileOpt.isPresent()) {

            log.info("original file has been removed, recreating original file based on encrypted one");

            originalFile = DataFactory.cloneBasicData(encryptedFile);
            originalFile.setUuid(encryptedFile.getOriginalFileUuid());

            try (InputStream fileInput = decryptedFileStream(encryptedFile,
                                                             userData.getUser(),
                                                             userEncryptionKey,
                                                             password)) {
                fs.save(filePath(userData, originalFile), fileInput);
            }

            userData.getFiles().add(originalFile);

        } else {

            log.info("original file exists - switching references only");

            originalFile = originalFileOpt.get();

        }

        message.getAttachments().set(attachmentIndex, originalFile.getUuid());

        message.setAttachments(Utils.unique(message.getAttachments()));

        deleteFile(userData, encryptedFile);

    }

    public void encrypt(UserData userData, String msgUuid, String fileUuid, String password) throws IOException {

        Message message = new UserDataHelper(userData).requireMessageByUuid(msgUuid);

        File file = new UserDataHelper(userData).requireFileByUuid(fileUuid);
        if (file.getEncryption() != null) {
            throw new BadRequestException("file is already encrypted");
        }
        new UserDataHelper(userData).verifyQuotaNotExceeded(file.getSize());

        int attachmentIndex = message.getAttachments().indexOf(file.getUuid());
        if (attachmentIndex == -1) {
            throw new BadRequestException("file is not connected to that message");
        }
        log.info("msg attachment index={}", attachmentIndex);

        File encryptedFile = fileEncryptionService.encryptFileByPassword(file, userData.getUser(), password);
        // this file is *only* attached to this message, it belongs to that message
        encryptedFile.setBelongsTo(message.getUuid());

        userData.getFiles().add(encryptedFile);

        message.getAttachments().set(attachmentIndex, encryptedFile.getUuid());

    }

    private InputStream decryptedFileStream(File file, User user, SecretKey userEncryptionKey, String encryptionPassword) throws IOException {
        return fileEncryptionService.getDecryptedFileStream(file, user, userEncryptionKey, encryptionPassword);
    }

}
