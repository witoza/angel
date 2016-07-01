package co.postscriptum.service;

import co.postscriptum.exceptions.BadRequestException;
import co.postscriptum.exceptions.InternalException;
import co.postscriptum.fs.FS;
import co.postscriptum.internal.UploadsEncryptionService;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class FileService {

    private final FS fs;

    private final UploadsEncryptionService uploadsEncryptionService;

    private final LoggedUserService loggedUserService;

    private InputStream decryptedFileStream(File file, String encryptionPassword) throws IOException {
        return uploadsEncryptionService.getDecryptedFileStream(
                file,
                loggedUserService.requireUserData().getUser(),
                loggedUserService.requireUserEncryptionKey(),
                encryptionPassword);
    }

    public ResponseEntity<InputStreamResource> openFile(String uuid) throws IOException {

        File file = loggedUserService.requireFileByUuid(uuid);

        if (file.getEncryption() != null) {
            throw new BadRequestException("preview not available for password encrypted file");
        }

        log.info("opening file name={}, uuid={}", file.getName(), file.getUuid());

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_TYPE, file.getMime())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .body(new InputStreamResource(decryptedFileStream(file, null)));
    }

    public List<File> getFiles() {
        return loggedUserService.requireUserData().getFiles();
    }

    public File upload(MultipartFile uploadingFile, String title) throws IOException {

        //set some sane limit
        if (getFiles().size() > 100) {
            throw new InternalException("too many files");
        }

        String fileName = uploadingFile.getOriginalFilename();
        if (StringUtils.isNotBlank(title)) {
            fileName = title;
        }

        if (uploadingFile.getSize() > 0) {
            loggedUserService.verifyQuotaNotExceeded(uploadingFile.getSize());
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

        uploadsEncryptionService.saveStreamToFile(
                file,
                loggedUserService.requireUserData().getUser(),
                loggedUserService.requireUserEncryptionKey(),
                uploadingFile.getInputStream());

        try {
            loggedUserService.verifyQuotaNotExceeded(file.getSize());
        } catch (Exception e) {
            fs.remove(filePath(file));
            throw e;
        }

        getFiles().add(file);

        log.info("file has been uploaded name={}, uuid={}", file.getName(), file.getUuid());

        return file;
    }

    private String filePath(File file, User owner) {
        return owner.getUuid() + "/" + file.getUuid();
    }

    private String filePath(File file) {
        return filePath(file, loggedUserService.requireUserData().getUser());
    }

    public void deleteFile(String uuid) throws IOException {
        deleteFile(loggedUserService.requireFileByUuid(uuid));
    }

    private void deleteFile(File file) throws IOException {

        log.info("deleting file name={}, uuid={}", file.getName(), file.getUuid());

        UserData userData = loggedUserService.requireUserData();

        fs.remove(filePath(file));

        userData.getMessages().forEach(m -> m.getAttachments().removeIf(att -> att.equals(file.getUuid())));
        userData.getFiles().removeIf(f -> f.getUuid().equals(file.getUuid()));

    }

    public FileDTO convertToDto(File file) {

        UserData userData = loggedUserService.requireUserData();

        FileDTO fdto = BO2DTOConverter.toFileDTO(userData, file);
        fdto.setMessages(userData.getMessages()
                                 .stream()
                                 .filter(m -> m.getAttachments().contains(file.getUuid()))
                                 .map(BO2DTOConverter::toMessageDTO)
                                 .collect(Collectors.toList()));

        return fdto;
    }

    public void decrypt(String msgUuid, String fileUuid, String encryptionPassword) throws IOException {

        Message message = loggedUserService.requireMessageByUuid(msgUuid);

        File encryptedFile = loggedUserService.requireFileByUuid(fileUuid);
        if (encryptedFile.getEncryption() == null) {
            throw new BadRequestException("can't decrypt not encrypted file");
        }

        int attachmentIndex = message.getAttachments().indexOf(encryptedFile.getUuid());
        if (attachmentIndex == -1) {
            throw new BadRequestException("file is not connected to that message");
        }

        Optional<File> originalFileOpt = loggedUserService.getFileByUuid(encryptedFile.getOriginalFileUuid());

        File originalFile;
        if (!originalFileOpt.isPresent()) {

            log.info("original file has been removed, recreating original file based on encrypted one");

            originalFile = DataFactory.cloneBasicData(encryptedFile);
            originalFile.setUuid(encryptedFile.getOriginalFileUuid());

            try (InputStream fileInput = decryptedFileStream(encryptedFile, encryptionPassword)) {
                fs.save(filePath(originalFile), fileInput);
            }

            getFiles().add(originalFile);

        } else {

            log.info("original file exists - switching references only");

            originalFile = originalFileOpt.get();

        }

        message.getAttachments().set(attachmentIndex, originalFile.getUuid());

        message.setAttachments(Utils.unique(message.getAttachments()));

        deleteFile(encryptedFile);

    }

    public void encrypt(String msgUuid, String fileUuid, String encryptionPassword) throws IOException {

        Message message = loggedUserService.requireMessageByUuid(msgUuid);

        File file = loggedUserService.requireFileByUuid(fileUuid);
        if (file.getEncryption() != null) {
            throw new BadRequestException("file is already encrypted");
        }
        loggedUserService.verifyQuotaNotExceeded(file.getSize());

        int attachmentIndex = message.getAttachments().indexOf(file.getUuid());
        if (attachmentIndex == -1) {
            throw new BadRequestException("file is not connected to that message");
        }
        log.info("msg attachment index={}", attachmentIndex);

        File encryptedFile =
                uploadsEncryptionService.encryptFileByPassword(file,
                                                               loggedUserService.requireUserData().getUser(),
                                                               encryptionPassword);
        // this file is *only* attached to this message, it belongs to that message
        encryptedFile.setBelongsTo(message.getUuid());

        getFiles().add(encryptedFile);

        message.getAttachments().set(attachmentIndex, encryptedFile.getUuid());

    }

}
