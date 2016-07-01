package co.postscriptum.service;

import co.postscriptum.db.DB;
import co.postscriptum.email.UserEmailService;
import co.postscriptum.exceptions.BadRequestException;
import co.postscriptum.exceptions.ForbiddenException;
import co.postscriptum.internal.AdminHelperService;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.DataFactory;
import co.postscriptum.model.bo.LoginAttempt;
import co.postscriptum.model.bo.RequiredAction;
import co.postscriptum.model.bo.RequiredAction.Type;
import co.postscriptum.model.bo.Trigger;
import co.postscriptum.model.bo.TriggerInternal;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.User.Role;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.bo.UserInternal;
import co.postscriptum.model.bo.UserPlan;
import co.postscriptum.model.dto.UserDTO;
import co.postscriptum.payment.BitcoinService;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import co.postscriptum.security.PasswordUtils;
import co.postscriptum.security.RSAOAEPUtils;
import co.postscriptum.security.TOTPHelperService;
import co.postscriptum.web.UserRest.UpdateUserDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class UserService {

    private final AdminHelperService adminHelperService;
    private final TOTPHelperService totpHelperService;
    private final UserEmailService userEmailService;
    private final LoggedUserService loggedUserService;
    private final BitcoinService bitcoinService;
    private final DB db;

    public static String guessScreenName(String email) {

        try {
            int pos = email.indexOf('@');
            String screenName = email.toLowerCase().substring(0, pos);

            StringBuilder sb = new StringBuilder();

            for (String part : screenName.split("\\.|\\+")) {
                sb.append(StringUtils.capitalize(part)).append(" ");
            }

            return sb.toString().trim();
        } catch (Exception e) {
            log.info("can't figure out screenName from email: {}" + email, e);
            return email;
        }

    }

    public Optional<SecretKey> getUserEncryptionKey() {
        return loggedUserService.getUserEncryptionKey();
    }

    private UserData requireUserData() {
        return loggedUserService.requireUserData();
    }

    public UserDTO getUserDTO() {

        UserData userData = requireUserData();
        User user = userData.getUser();

        UserDTO dto = new UserDTO();
        dto.setUuid(user.getUuid());
        dto.setActive(user.isActive());
        dto.setTosAccepted(user.isTosAccepted());
        dto.setUsername(user.getUsername());
        dto.setLang(userData.getInternal().getLang());
        dto.setRole(user.getRole());
        dto.setTrigger(user.getTrigger());
        dto.setTriggerInternal(userData.getInternal().getTriggerInternal());
        dto.setAllowPasswordReset(userData.getInternal().isAllowPasswordReset());
        dto.setVerifyUnknownBrowsers(userData.getInternal().isVerifyUnknownBrowsers());

        dto.setCreationTime(userData.getInternal().getCreationTime());
        dto.setQuotaBytes(userData.getInternal().getQuotaBytes());
        dto.setScreenName(userData.getInternal().getScreenName());

        if (userData.getUser().getRole() == Role.user) {

            dto.setUsedSpaceBytes(loggedUserService.getUserUsedSpaceBytes());
            dto.setValidAesKey(getUserEncryptionKey().isPresent());
            dto.setNeedPayment(needPayment());

        }

        dto.setUnreadNotifs(
                userData.getNotifications()
                        .stream()
                        .filter(n -> !n.isRead())
                        .count());

        dto.setUserPlan(userData.getInternal().getUserPlan());
        dto.setTotpUri(totpHelperService.getTotpUri(userData));
        dto.setTotpRecoveryEmail(userData.getInternal().getTotpRecoveryEmail());
        dto.setEnableTotp(userData.getInternal().isEnableTotp());

        return dto;
    }

    public void deleteUser(String loginPassword) {

        loggedUserService.verifyLoginPasswordIsCorrect(loginPassword);

        db.removeUserByUuid(requireUserData().getUser().getUuid());

    }

    private boolean needPayment() {
        UserData userData = requireUserData();

        UserPlan userPlan = userData.getInternal().getUserPlan();

        return userPlan.getPaidUntil() < System.currentTimeMillis();
    }

    public void requestForStorage(int numberOfMb) {

        UserData userData = requireUserData();

        new UserDataHelper(userData).addNotification("User issued request for storage increase of " + numberOfMb + " MB");

        RequiredAction ra = DataFactory.newRequiredAction(userData, Type.storage_increase);
        ra.getDetails().put("numberOfMb", "" + numberOfMb);

        adminHelperService.addAdminRequiredAction(ra);

    }

    public void changeLoginPassword(String loginPassword, String newLoginPassword) {

        loggedUserService.verifyLoginPasswordIsCorrect(loginPassword);

        UserInternal userInternal = requireUserData().getInternal();

        if (!loggedUserService.isUserAdmin()) {

            getUserEncryptionKey()
                    .ifPresent(encryptionKey -> {
                        log.info("user encryption key is there, encrypting it by a new password");

                        userInternal.setEncryptionKey(AESGCMUtils.encryptByPassword(newLoginPassword, encryptionKey.getEncoded()));
                    });

        }

        userInternal.setPasswordHash(PasswordUtils.hashPassword(newLoginPassword));

    }

    private String convertToTriggerValidEmails(String emails) {
        return String.join("; ", Utils.extractValidEmails(emails));
    }

    public void updateUser(UpdateUserDTO params) {

        UserData userData = requireUserData();
        User user = userData.getUser();

        if (params.getAllowPasswordReset() != null) {
            userData.getInternal().setAllowPasswordReset(params.getAllowPasswordReset());
        }
        if (params.getScreenName() != null) {
            userData.getInternal().setScreenName(params.getScreenName());
        }
        if (params.getTosAccepted() != null) {
            user.setTosAccepted(params.getTosAccepted());
        }
        if (params.getVerifyUnknownBrowsers() != null) {
            userData.getInternal().setVerifyUnknownBrowsers(params.getVerifyUnknownBrowsers());
        }
        if (params.getLang() != null) {
            userData.getInternal().setLang(params.getLang());
        }
        if (params.getTotpRecoveryEmail() != null) {
            String email = params.getTotpRecoveryEmail();
            if (!StringUtils.isEmpty(email)) {
                if (!Utils.isValidEmail(email)) {
                    throw new BadRequestException("email address: " + email + " is invalid");
                }
            }
            userData.getInternal().setTotpRecoveryEmail(email);
        }

        if (params.getTriggerInternal() != null) {

            TriggerInternal triggerInternalParams = params.getTriggerInternal();
            TriggerInternal triggerInternal = userData.getInternal().getTriggerInternal();
            if (triggerInternalParams.getXemails() != null) {
                triggerInternal.setXemails(convertToTriggerValidEmails(triggerInternalParams.getXemails()));
            }
            if (triggerInternalParams.getYemails() != null) {
                triggerInternal.setYemails(convertToTriggerValidEmails(triggerInternalParams.getYemails()));
            }
            if (triggerInternalParams.getZemails() != null) {
                triggerInternal.setZemails(convertToTriggerValidEmails(triggerInternalParams.getZemails()));
            }
        }

        if (params.getTrigger() != null) {

            Trigger triggerParams = params.getTrigger();
            Trigger trigger = user.getTrigger();
            if (triggerParams.getEnabled() != null) {
                trigger.setEnabled(triggerParams.getEnabled());
            }
            if (triggerParams.getTimeUnit() != null) {
                trigger.setTimeUnit(triggerParams.getTimeUnit());
            }
            if (triggerParams.getX() != null) {
                trigger.setX(triggerParams.getX());
            }
            if (triggerParams.getY() != null) {
                trigger.setY(triggerParams.getY());
            }
            if (triggerParams.getZ() != null) {
                trigger.setZ(triggerParams.getZ());
            }
            if (triggerParams.getW() != null) {
                trigger.setW(triggerParams.getW());
            }

        }

    }

    public void setEncryptionKey(String loginPassword, String encryptionKey) {

        loggedUserService.verifyLoginPasswordIsCorrect(loginPassword);

        UserInternal internal = requireUserData().getInternal();

        if (StringUtils.isEmpty(encryptionKey)) {

            log.info("empty key => removing key");

            loggedUserService.setUserEncryptionKey(null);

            internal.setEncryptionKey(null);
            internal.setEncryptionKeyEncryptedByAdminPublicKey(null);

        } else {

            log.info("key present => installing it");

            byte[] secretKey = Utils.base32decode(encryptionKey);

            loggedUserService.setUserEncryptionKey(secretKey);

            internal.setEncryptionKey(AESGCMUtils.encryptByPassword(loginPassword, secretKey));
            internal.setEncryptionKeyEncryptedByAdminPublicKey(RSAOAEPUtils.encrypt(secretKey,
                                                                                    adminHelperService.getAdminPublicKey()));

        }

    }

    public List<String> sendTriggerAfterX(boolean sendEmailOnlyToUser) {
        return userEmailService.sendTriggerAfterX(requireUserData(), sendEmailOnlyToUser);
    }

    public List<String> sendTriggerAfterY(boolean sendEmailOnlyToUser) {
        return userEmailService.sendTriggerAfterY(requireUserData(), sendEmailOnlyToUser);
    }

    public List<String> sendTriggerAfterZ(boolean sendEmailOnlyToUser) {
        return userEmailService.sendTriggerAfterZ(requireUserData(), sendEmailOnlyToUser);
    }

    public void unloadUser() {

        try {
            db.unloadUserByUuid(requireUserData().getUser().getUuid());
        } catch (IOException e) {
            log.error("problem with user unloading", e);
        }

    }

    public void enable2FA(String totpToken) {
        log.info("enabling 2FA");

        UserData userData = requireUserData();

        if (totpHelperService.isTokenValid(userData, totpToken)) {
            log.info("provided token is correct, enabling 2FA");

            userData.getInternal().setEnableTotp(true);
        } else {
            throw new ForbiddenException("Provided token is not correct");
        }

    }

    public void disable2FA() {
        log.info("disabling 2FA");
        requireUserData().getInternal().setEnableTotp(false);
    }

    public void generateTotpSecret() {
        log.info("generate TotpSecret");
        requireUserData().getInternal().setTotpSecret(AESKeyUtils.randomByteArray(8));
        disable2FA();
    }

    public List<LoginAttempt> getLoginHistory() {
        return requireUserData().getInternal().getLoginHistory();
    }

    public ResponseEntity<InputStreamResource> getTotpUriQr() {
        return totpHelperService.getTotpUriQr(requireUserData());
    }

    public String getPaymentBitcoinAddress() {
        if (!needPayment()) {
            throw new ForbiddenException("can't obtain payment address when account is paid off");
        }
        return bitcoinService.getPaymentForUser(requireUserData()).getBtcAddress();
    }

}
