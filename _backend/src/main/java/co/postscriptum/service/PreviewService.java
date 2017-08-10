package co.postscriptum.service;

import co.postscriptum.controller.PreviewController;
import co.postscriptum.db.Account;
import co.postscriptum.db.DB;
import co.postscriptum.exception.BadRequestException;
import co.postscriptum.exception.ForbiddenException;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.BO2DTOConverter;
import co.postscriptum.model.bo.File;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.ReleaseItem;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.dto.MessageDTO;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import co.postscriptum.security.UserEncryptionKeyService;
import co.postscriptum.web.AuthenticationHelper;
import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class PreviewService {

    private final FileService fileService;

    private final DB db;

    private final UserEncryptionKeyService userEncryptionKeyService;

    // can be invoked from normal mode and from preview
    public Pair<File, InputStream> download(PreviewController.DownloadFileDTO form) throws IOException {

        Account fileAccount = db.requireAccountByUuid(form.getUser_uuid());
        try {
            fileAccount.lock();
            db.loadAccount(fileAccount);

            UserData userData = fileAccount.getUserData();

            File file = userData.requireFileByUuid(form.getFile_uuid());

            log.info("Downloading file name: {}, uuid: {}", file.getName(), file.getUuid());

            SecretKey encryptionKey;

            if (!StringUtils.isEmpty(form.getMsg_uuid())) {

                log.info("Preview mode download");

                Message message = userData.requireMessageByUuid(form.getMsg_uuid());

                if (!message.getAttachments().contains(file.getUuid())) {
                    throw new BadRequestException("File does not belong to that message");
                }

                ReleaseItemWithKey releaseItem = verifyCanPreviewMessage(userData,
                                                                         message,
                                                                         form.getReleaseKey(),
                                                                         form.getEncryptionKey(),
                                                                         form.getRecipientKey());

                encryptionKey = releaseItem.getEncryptionKey();

                if (encryptionKey == null) {
                    throw new BadRequestException("Expected encrypted message");
                }

            } else {

                log.info("Edit mode download");

                if (!AuthenticationHelper.isUserLogged(fileAccount.getUserData().getUser().getUsername())) {
                    throw new ForbiddenException("File does not belong to the logged account");
                }

                encryptionKey = userEncryptionKeyService.requireEncryptionKey();

            }

            return Pair.of(file, fileService.getFileContent(userData, file, encryptionKey, form.getEncryptionPassword()));

        } finally {
            fileAccount.unlock();
        }
    }

    private SecretKey getEncryptionKey(String aesKey) {
        if (StringUtils.isEmpty(aesKey)) {
            return null;
        }

        try {
            return AESKeyUtils.toSecretKey(Utils.base32decode(aesKey));
        } catch (Exception e) {
            log.warn("Provided data is not a proper AES key: {}", Utils.basicExceptionInfo(e));
        }
        return null;
    }

    private ReleaseItemWithKey verifyCanPreviewMessage(UserData userData,
                                                       Message message,
                                                       String releaseKey,
                                                       String userEncryptionKey,
                                                       String recipientKey) {

        if (AuthenticationHelper.isUserLogged(userData.getUser().getUsername())) {

            log.info("Logged account is the owner of the message");

            ReleaseItem releaseItem = new ReleaseItem();
            releaseItem.setFirstTimeAccess(System.currentTimeMillis());
            releaseItem.setRecipient(StringUtils.join(message.getRecipients(), ", "));

            SecretKey secretKey = userEncryptionKeyService.getEncryptionKey()
                                                          .orElseGet(() -> getEncryptionKey(userEncryptionKey));

            return new ReleaseItemWithKey(releaseItem, secretKey);

        }

        if (message.getRelease() == null) {
            throw new ForbiddenException("Message hasn't been yet released");
        }

        ReleaseItem releaseItem = message.requireReleaseItem(releaseKey);
        if (releaseItem.getFirstTimeAccess() == 0) {
            releaseItem.setFirstTimeAccess(System.currentTimeMillis());
            userData.addNotification("Message '" + message.getTitle() + "' has been first time accessed by " + releaseItem.getRecipient());
        }

        if (releaseItem.getUserEncryptionKeyEncodedByRecipientKey() == null) {

            log.info("No EncryptionKey in UserData, getting one from user input params");

            return new ReleaseItemWithKey(releaseItem, getEncryptionKey(userEncryptionKey));

        } else {

            log.info("Decrypting User's EncryptionKey by RecipientKey");

            SecretKey recipientSecretKey = AESKeyUtils.toSecretKey(Utils.base64decode(recipientKey));

            SecretKey userKey = AESKeyUtils.toSecretKey(AESGCMUtils.decrypt(recipientSecretKey,
                                                                            releaseItem.getUserEncryptionKeyEncodedByRecipientKey()));

            return new ReleaseItemWithKey(releaseItem, userKey);

        }

    }

    public MessageDTO decrypt(PreviewController.PreviewBaseDTO params) {

        return db.withLoadedAccountByUuid(params.getUser_uuid(), account -> {

            UserData userData = account.getUserData();

            Message message = userData.requireMessageByUuid(params.getMsg_uuid());

            if (message.getEncryption() == null) {
                throw new BadRequestException("Expected encrypted message");
            }

            ReleaseItemWithKey releaseItem = verifyCanPreviewMessage(userData,
                                                                     message,
                                                                     params.getReleaseKey(),
                                                                     params.getEncryptionKey(),
                                                                     params.getRecipientKey());

            SecretKey encryptionKey = releaseItem.getEncryptionKey();
            if (encryptionKey == null) {
                throw new BadRequestException("Need to have encryption key to decrypt message");
            }

            MessageDTO mdto = BO2DTOConverter.toMessageDTO(message);
            mdto.setReleaseItem(releaseItem.releaseItem);
            try {
                mdto.setContent(message.getContent(encryptionKey, params.getEncryptionPassword()));
            } catch (Exception e) {
                throw new ForbiddenException("Invalid password", e);
            }
            setFiles(mdto, userData);

            return mdto;

        });

    }

    public Map<String, Object> requireMessage(PreviewController.PreviewBaseDTO params) {

        return db.withLoadedAccountByUuid(params.getUser_uuid(), account -> {
            UserData userData = account.getUserData();

            Message message = userData.requireMessageByUuid(params.getMsg_uuid());

            ReleaseItemWithKey releaseItem = verifyCanPreviewMessage(userData,
                                                                     message,
                                                                     params.getReleaseKey(),
                                                                     params.getEncryptionKey(),
                                                                     params.getRecipientKey());

            MessageDTO mdto = BO2DTOConverter.toMessageDTO(message);
            mdto.setReleaseItem(releaseItem.getReleaseItem());

            boolean encryptionKeyValid = false;

            if (releaseItem.getEncryptionKey() != null) {

                byte[] raw = null;
                try {
                    raw = message.decryptFirstLayerContent(releaseItem.getEncryptionKey());
                    encryptionKeyValid = true;
                } catch (Exception e) {
                    log.warn("Invalid EncryptionKey: {}", Utils.basicExceptionInfo(e));
                }

                // 'content' should be set only when message not password protected
                if (message.getEncryption() == null) {
                    mdto.setContent(Utils.asString(raw));
                    setFiles(mdto, userData);
                }

            }

            return ImmutableMap.of(
                    "msg", mdto,
                    "invalid_aes_key", !encryptionKeyValid,
                    "from", userData.getInternal().getScreenName() + " (" + userData.getUser().getUsername() + ")",
                    "lang", ObjectUtils.firstNonNull(message.getLang(), userData.getInternal().getLang()));
        });

    }

    private void setFiles(MessageDTO mdto, UserData userData) {
        mdto.setFiles(userData.getFiles()
                              .stream()
                              .filter(f -> mdto.getAttachments().contains(f.getUuid()))
                              .map(f -> BO2DTOConverter.toFileDTO(userData, f))
                              .collect(Collectors.toList()));

    }

    @Value
    private static class ReleaseItemWithKey {

        ReleaseItem releaseItem;

        SecretKey encryptionKey;

    }

}
