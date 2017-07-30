package co.postscriptum.service;

import co.postscriptum.db.Account;
import co.postscriptum.db.DB;
import co.postscriptum.email.EmailTemplateService;
import co.postscriptum.email.UserEmailService;
import co.postscriptum.exception.BadRequestException;
import co.postscriptum.exception.ForbiddenException;
import co.postscriptum.exception.InternalException;
import co.postscriptum.internal.I18N;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.DataFactory;
import co.postscriptum.model.bo.Lang;
import co.postscriptum.model.bo.LoginAttempt;
import co.postscriptum.model.bo.RequiredAction.Type;
import co.postscriptum.model.bo.Trigger;
import co.postscriptum.model.bo.Trigger.Stage;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.User.Role;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.bo.UserInternal;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import co.postscriptum.security.MyAuthenticationToken;
import co.postscriptum.security.PasswordUtils;
import co.postscriptum.security.RSAOAEPUtils;
import co.postscriptum.security.RequestMetadata;
import co.postscriptum.security.TOTPHelperService;
import co.postscriptum.security.VerifiedUsers;
import co.postscriptum.stk.ShortTimeKey;
import co.postscriptum.stk.ShortTimeKeyService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class LoginService {

    private final EmailTemplateService emailTemplateService;

    private final EnvelopeCreatorService envelopeCreatorService;

    private final TOTPHelperService totpHelperService;

    private final AdminHelperService adminHelperService;

    private final UserEmailService userEmailService;

    private final ShortTimeKeyService shortTimeKeyService;

    private final I18N i18n;

    private final DB db;

    private void addNotification(UserData userData, String message) {
        new UserDataHelper(userData).addNotification(message);
    }

    private void verifyIsActive(User user) {
        if (!user.isActive()) {
            throw new ForbiddenException("user " + user.getUsername() + " is not active");
        }
    }

    private void resurrectUser(UserData userData) {
        if (userData.getUser().getTrigger().getStage() == Stage.released) {

            log.info("user is resurrecting, invalidating all msg releases");

            Trigger trigger = userData.getUser().getTrigger();
            trigger.setStage(Stage.beforeX);
            trigger.setReadyToBeReleasedTime(0);
            trigger.setHaveBeenReleasedTime(0);

            userData.getMessages().forEach(message -> {
                message.setRelease(null);
            });

            addNotification(userData,
                            "User has logged in after his messages had been released.\n" +
                                    "All message releases have just been invalidated, recipients can no longer display them.");
        }
    }

    private MyAuthenticationToken authenticate(RequestMetadata metadata,
                                               UserData userData,
                                               String password,
                                               String totpToken,
                                               String verifyToken,
                                               VerifiedUsers verifiedUsers) {

        User user = userData.getUser();
        UserInternal internal = userData.getInternal();

        String username = user.getUsername();

        log.info("authing: {}", username);

        verifyIsActive(user);
        verifyAccountNotLocked(userData);
        verifyProvidedPassword(userData, password);

        log.info("user has been authenticated");

        user.setLastAccess(System.currentTimeMillis());

        if (userData.getInternal().isVerifyUnknownBrowsers() && !verifiedUsers.isUserVerified(userData)) {

            log.info("user login is from not verified browser");

            Optional<ShortTimeKey> loginTokenSTP = shortTimeKeyService.getByKey(verifyToken, ShortTimeKey.Type.login);

            if (!loginTokenSTP.isPresent() || !StringUtils.equals(loginTokenSTP.get().getUsername(), username)) {

                log.info("the loginToken is not correct");

                ShortTimeKey newLoginTokenSTP = shortTimeKeyService.create(username, ShortTimeKey.Type.login);

                userEmailService.sendUserLoginVerification(userData, newLoginTokenSTP, metadata);

                throw new ForbiddenException("Please provide login token");
            }

            verifiedUsers.markAsVerified(userData);
        }

        verifyTotpToken(userData, totpToken);

        byte[] encryptionKey = null;
        if (user.getRole() == Role.user) {

            resurrectUser(userData);

            if (internal.getEncryptionKey() != null) {
                encryptionKey = AESGCMUtils.decryptByPassword(password, internal.getEncryptionKey());
            }

        }

        return new MyAuthenticationToken(username, user.getRole(), encryptionKey,
                                         new SimpleGrantedAuthority("ROLE_" + user.getRole().toString().toUpperCase()));

    }

    private void verifyTotpToken(UserData userData, String totpToken) {
        if (userData.getInternal().isEnableTotp()) {
            log.info("2FA is enabled, checking OTP token");

            if (StringUtils.isEmpty(totpToken)) {
                throw new ForbiddenException("Please provide security token");
            }

            if (!totpHelperService.isTokenValid(userData, totpToken)) {
                throw new ForbiddenException("Invalid security token");
            }
        }
    }

    private void verifyProvidedPassword(UserData userData, String password) {
        log.info("verifying password");
        UserInternal internal = userData.getInternal();

        if (!PasswordUtils.checkPasswordHash(password, internal)) {

            internal.getInvalidLoginTs().add(System.currentTimeMillis());
            internal.getInvalidLoginTs().removeIf(ts -> System.currentTimeMillis() - ts > Utils.minutesInMs(20));
            if (internal.getInvalidLoginTs().size() > 3) {

                userEmailService.sendUserInvalid3Logins(userData);

                addNotification(userData,
                                "You provided invalid password 3 times in a row in the last 30 minutes, your account has been locked for 5 minutes");

                log.warn("locking out user account for 5 minutes");
                internal.setAccountLockedUntil(System.currentTimeMillis() + Utils.minutesInMs(5));
                internal.getInvalidLoginTs().clear();

            }

            throw new ForbiddenException("invalid password");
        }
        internal.setAccountLockedUntil(0);
        internal.getInvalidLoginTs().clear();
    }

    private void verifyAccountNotLocked(UserData userData) {
        if (userData.getInternal().getAccountLockedUntil() > System.currentTimeMillis()) {
            throw new ForbiddenException(
                    "account is locked until " + Utils.format(userData.getInternal().getAccountLockedUntil()));
        }
    }

    private void addLoginAttempt(Account account, RequestMetadata metadata, String type) {

        List<LoginAttempt> loginHistory = account.getUserData().getInternal().getLoginHistory();

        loginHistory.add(LoginAttempt.builder()
                                     .time(System.currentTimeMillis())
                                     .ip(metadata.getRemoteIp())
                                     .type(type)
                                     .build());

        Utils.limit(loginHistory, 20);

    }

    public void preregister(String username, Lang lang, RequestMetadata metadata) {

        Optional<Account> possibleAccount = db.getAccountByUsername(username);
        if (possibleAccount.isPresent()) {
            log.info("user already exists");

            db.withLoadedAccount(possibleAccount, account -> {

                userEmailService.sendUserAccountAlreadyExists(account.getUserData());

                addLoginAttempt(account, metadata, "someone tries to register user with your email address");

            });

        } else {
            log.info("user does not exist, creating ShortTimeKey and sending email with token");

            ShortTimeKey shortTimeKey = shortTimeKeyService.create(username, ShortTimeKey.Type.register_user);

            userEmailService.sendUserPreregisters(username, lang, shortTimeKey);

        }

    }

    public Authentication register(String shortTimeKey,
                                   String password,
                                   Lang lang,
                                   RequestMetadata metadata,
                                   VerifiedUsers verifiedUsers) throws IOException {

        ShortTimeKey stk = shortTimeKeyService.require(shortTimeKey, ShortTimeKey.Type.register_user);

        shortTimeKeyService.removeKey(stk);

        String username = stk.getUsername();

        if (db.hasAccountByUsername(username)) {
            throw new InternalException("user already exists");
        }

        User user = DataFactory.newUser();
        user.setUsername(username);
        user.setActive(true);
        user.setTosAccepted(false);

        UserInternal userInternal = DataFactory.newUserInternal(password, adminHelperService.getAdminPublicKey());
        userInternal.setTotpRecoveryEmail(user.getUsername());
        userInternal.setScreenName(UserService.guessScreenName(username));
        userInternal.setQuotaBytes(100 * 1024 * 1024);
        userInternal.setLang(lang);

        userInternal.getTriggerInternal().setXemails(username);

        UserData userData = DataFactory.newUserData();
        userData.setUser(user);
        userData.setInternal(userInternal);
        addNotification(userData, "Your account has been activated");

        Account account = db.insertUser(userData);

        verifiedUsers.markAsVerified(userData);

        addLoginAttempt(account, metadata, "user registered");

        return authenticate(metadata, userData, password, null, null, verifiedUsers);


    }

    public Authentication login(String username,
                                String password,
                                String totpToken,
                                String verifyToken,
                                RequestMetadata metadata,
                                VerifiedUsers verifiedUsers) {

        return db.withLoadedAccountByUsername(username, account -> {

            try {

                Authentication authentication =
                        authenticate(metadata, account.getUserData(), password, totpToken, verifyToken, verifiedUsers);

                addLoginAttempt(account, metadata, "success");

                return authentication;

            } catch (ForbiddenException | BadRequestException e) {

                addLoginAttempt(account, metadata, "failed: " + e.getMessage());
                throw e;

            } catch (Exception e) {

                addLoginAttempt(account, metadata, "internal error");
                throw e;

            }
        });

    }

    public String alive(String userUuid, String key) {

        return db.withLoadedAccountByUuid(userUuid, account -> {
            UserData userData = account.getUserData();
            User user = userData.getUser();

            verifyIsActive(user);

            if (!userData.getInternal().getTriggerInternal().getResetKey().equals(key)) {
                throw new ForbiddenException("invalid reset key for that user");
            }

            user.setLastAccess(System.currentTimeMillis());

            Lang lang = userData.getInternal().getLang();
            Map<String, Object> context = envelopeCreatorService.createContext(userData);

            Trigger trigger = user.getTrigger();
            final String content;

            if (!trigger.getEnabled()) {
                content = i18n.translate(lang, "%alive.trigger_not_active%", context);
            } else {
                long totalTime = trigger.getX() + trigger.getY() + trigger.getZ() + trigger.getW();
                ZonedDateTime releaseTm = ZonedDateTime.now().plus(totalTime, trigger.getTimeUnit());
                context.put("totalTime", totalTime);
                context.put("timeUnit",
                            i18n.translate(lang, "%time_unit." + trigger.getTimeUnit().toString().toLowerCase() + "%"));
                context.put("releaseTm", Utils.format(releaseTm));
                content = i18n.translate(lang, "%alive.trigger_reset%", context);
            }

            String footer = i18n.translate(lang, "%alive_footer%", context);

            return emailTemplateService.getFormattedContent("", content, footer);
        });

    }

    public String recallTotpKey(String username, String password) {

        return db.withLoadedAccountByUsername(username, account -> {
            UserData userData = account.getUserData();
            User user = userData.getUser();

            verifyIsActive(user);

            if (!PasswordUtils.checkPasswordHash(password, userData.getInternal())) {
                throw new ForbiddenException("invalid password");
            }

            ShortTimeKey stk = shortTimeKeyService.create(username, ShortTimeKey.Type.recall_otp_key);

            userEmailService.sendTOTPTokenDetails(userData, stk);

            String recoveryEmail = userData.getInternal().getTotpRecoveryEmail();

            addNotification(userData, "OTP key details has been sent to " + recoveryEmail);

            return recoveryEmail;
        });

    }

    public void resetPassword(String username) {

        db.withLoadedAccountByUsername(username, account -> {
            UserData userData = account.getUserData();

            if (!userData.getInternal().isAllowPasswordReset()) {
                throw new ForbiddenException("user didn't allow to reset password");
            }

            verifyIsActive(userData.getUser());

            if (userData.getInternal().getEncryptionKeyEncryptedByAdminPublicKey() != null) {
                adminHelperService.addAdminRequiredAction(DataFactory.newRequiredAction(userData, Type.new_user_password));

                userEmailService.sendUserResetsPasswordReq(userData);
            } else {

                ShortTimeKey stk = shortTimeKeyService.create(userData, ShortTimeKey.Type.password_change_req);
                userEmailService.sendAdminApprovedPasswordChange(userData, stk, null);

            }
        });

    }

    public void changePasswordByResetKey(String resetPasswordKey,
                                         String extraDataEncryptionKey,
                                         String newLoginPassword) {

        ShortTimeKey stk = shortTimeKeyService.require(resetPasswordKey, ShortTimeKey.Type.password_change_req);

        db.withLoadedAccountByShortTimeKey(stk, account -> {

            UserData userData = account.getUserData();

            if (!userData.getInternal().isAllowPasswordReset()) {
                throw new ForbiddenException("user not allowed to reset password");
            }

            verifyIsActive(userData.getUser());

            UserInternal internal = userData.getInternal();

            internal.setAccountLockedUntil(0);
            internal.getInvalidLoginTs().clear();

            log.info("changing password");

            internal.setPasswordHash(PasswordUtils.hashPassword(newLoginPassword));
            addNotification(userData, "User's password has been changed");

            if (internal.getEncryptionKeyEncryptedByAdminPublicKey() != null) {

                byte[] userEncryptionKey =
                        AESGCMUtils.decrypt(AESKeyUtils.toSecretKey(Utils.base64decode(extraDataEncryptionKey)),
                                            stk.getExtraData());

                internal.setEncryptionKey(AESGCMUtils.encryptByPassword(newLoginPassword, userEncryptionKey));
                internal.setEncryptionKeyEncryptedByAdminPublicKey(RSAOAEPUtils.encrypt(userEncryptionKey,
                                                                                        adminHelperService.getAdminPublicKey()));

            }

            shortTimeKeyService.removeKey(stk);

        });

    }

    public String recallTotpKeyDetailsInfo(String shortTimeKey) {

        ShortTimeKey stk = shortTimeKeyService.require(shortTimeKey, ShortTimeKey.Type.recall_otp_key);

        return db.withLoadedAccountByShortTimeKey(stk, account -> {
            UserData userData = account.getUserData();

            Map<String, Object> context = envelopeCreatorService.createContext(userData);

            context.put("shortTimeKey", shortTimeKey);
            context.put("key_uri", totpHelperService.getTotpUri(account.getUserData()));

            Lang lang = userData.getInternal().getLang();

            String content = i18n.translate(lang, "%totp_details%", context);
            String footer = i18n.translate(lang, "%alive_footer%", context);

            return emailTemplateService.getFormattedContent("", content, footer);
        });

    }

    public ResponseEntity<InputStreamResource> recallTotpKeyDetailsQr(String shortTimeKey) {

        ShortTimeKey stk = shortTimeKeyService.require(shortTimeKey, ShortTimeKey.Type.recall_otp_key);

        return db.withLoadedAccountByShortTimeKey(stk, account -> {
            return totpHelperService.getTotpUriQr(account.getUserData());
        });

    }

}
