package co.postscriptum.service;

import co.postscriptum.db.Account;
import co.postscriptum.db.DB;
import co.postscriptum.exception.ExceptionBuilder;
import co.postscriptum.exception.ForbiddenException;
import co.postscriptum.model.bo.File;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.security.AESKeyUtils;
import co.postscriptum.security.MyAuthenticationToken;
import co.postscriptum.security.PasswordUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Optional;

@Component
public class LoggedUserService {

    @Autowired
    private DB db;

    private Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private UserDataHelper userDataHelper() {
        return new UserDataHelper(requireUserData());
    }

    public Optional<File> getFileByUuid(String uuid) {
        return userDataHelper().getFileByUuid(uuid);
    }

    public Message requireMessageByUuid(String uuid) {
        return userDataHelper().requireMessageByUuid(uuid);
    }

    public File requireFileByUuid(String uuid) {
        return userDataHelper().requireFileByUuid(uuid);
    }

    private MyAuthenticationToken getMyAuthenticationToken() {
        return (MyAuthenticationToken) getAuthentication();
    }

    public void setUserEncryptionKey(byte[] encryptionKey) {
        getMyAuthenticationToken().setKey(encryptionKey);
    }

    public Optional<SecretKey> getUserEncryptionKey() {
        return getMyAuthenticationToken()
                .getKey()
                .map(AESKeyUtils::toSecretKey);
    }

    public SecretKey requireUserEncryptionKey() {
        return getUserEncryptionKey()
                .orElseThrow(ExceptionBuilder.badRequest("missing user encryption key"));
    }

    public UserData requireUserData() {

        String username = getAuthentication().getPrincipal().toString();
        Account account = db.requireAccountByUsername(username);

        account.assertLockIsHeldByCurrentThread();

        db.loadAccount(account);

        return account.getUserData();
    }

    public void verifyLoginPasswordIsCorrect(String loginPassword) {
        if (!PasswordUtils.checkPasswordHash(loginPassword, requireUserData().getInternal())) {
            throw new ForbiddenException("invalid password");
        }
    }

    public long getUserUsedSpaceBytes() {
        long total = 0;

        for (File file : requireUserData().getFiles()) {
            total += file.getSize();
            total += file.getName().length();
        }
        for (Message message : requireUserData().getMessages()) {
            total += message.getContent().getCt().length;
            total += message.getTitle().length();
        }
        return total;
    }

    private long kb(long bytes) {
        return bytes / 1024;
    }

    public void verifyQuotaNotExceeded(long addBytes) {

        long usedBytes = getUserUsedSpaceBytes();
        long quotaBytes = requireUserData().getInternal().getQuotaBytes();

        if (usedBytes + addBytes > quotaBytes) {
            throw new ForbiddenException(
                    "You don't have enough storage space to do that. Increase your quota and try again. Currently used: "
                            + kb(usedBytes) + "kb, would be: " + kb(usedBytes + addBytes) + "kb, quota: " + kb(quotaBytes) + "kb.");

        }
    }

    public boolean isUserAdmin() {
        return requireUserData().getUser().getRole() == User.Role.admin;
    }

    public boolean isUserLogged() {
        return getAuthentication() instanceof MyAuthenticationToken;
    }

    public String getLoggedUsername() {
        return getAuthentication().getName();
    }
}
