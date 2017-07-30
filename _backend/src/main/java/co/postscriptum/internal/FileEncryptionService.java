package co.postscriptum.internal;

import co.postscriptum.exception.BadRequestException;
import co.postscriptum.fs.FS;
import co.postscriptum.model.bo.DataFactory;
import co.postscriptum.model.bo.File;
import co.postscriptum.model.bo.PasswordEncryption;
import co.postscriptum.model.bo.User;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
@Component
public class FileEncryptionService {

    @Autowired
    private FS fs;

    public File encryptFileByPassword(@NonNull File file,
                                      @NonNull User owner,
                                      @NonNull String encryptionPassword) throws IOException {

        log.info("encrypting file uuid={} by password", file.getUuid());

        File encryptedFile = DataFactory.cloneBasicData(file);
        encryptedFile.setUuid(Utils.randKey("EF"));
        encryptedFile.setOriginalFileUuid(file.getUuid());

        OutputStream saveTo = fs.saveTo(getFilePath(encryptedFile, owner));

        byte[] salt = AESKeyUtils.randomSalt();

        SecretKey passwordKey = AESKeyUtils.deriveKey(encryptionPassword, salt);

        try (InputStream fis = fs.load(getFilePath(file, owner), true)) {

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

    public void saveStreamToFile(@NonNull File file,
                                 @NonNull User owner,
                                 @NonNull SecretKey encryptionKey,
                                 @NonNull InputStream input) throws IOException {

        OutputStream saveTo = fs.saveTo(getFilePath(file, owner));

        try (InfoInputStream fis = new InfoInputStream(input)) {

            file.setIv(AESGCMUtils.encryptStream(encryptionKey, fis, saveTo, new String[]{file.getUuid()}));
            file.setSha1(fis.getSha1());
            file.setSize(fis.getSize());

        } finally {
            saveTo.close();
        }
    }

    public InputStream getDecryptedFileStream(@NonNull File file,
                                              @NonNull User owner,
                                              @NonNull SecretKey encryptionKey,
                                              String encryptionPassword) throws IOException {

        InputStream fis = fs.load(getFilePath(file, owner), true);

        if (file.getEncryption() != null) {
            log.info("file is password encrypted");

            if (encryptionPassword == null) {
                throw new BadRequestException("encryptionPassword is required to decrypt file");
            }

            SecretKey passwordKey = AESKeyUtils.deriveKey(encryptionPassword, file.getEncryption().getSalt());

            InputStream dataStream = AESGCMUtils.decryptedStream(fis, passwordKey, file.getEncryption().getIv(), new String[]{});

            return AESGCMUtils.decryptedStream(dataStream,
                                               encryptionKey,
                                               file.getIv(),
                                               new String[]{file.getOriginalFileUuid()});

        } else {
            log.info("file is not password encrypted");

            return AESGCMUtils.decryptedStream(fis, encryptionKey, file.getIv(), new String[]{file.getUuid()});
        }

    }

    private String getFilePath(File file, User owner) {
        return owner.getUuid() + "/" + file.getUuid();
    }

}
