package co.postscriptum.service;

import co.postscriptum.controller.UserController.UpdateUserDTO;
import co.postscriptum.db.DB;
import co.postscriptum.email.UserEmailService;
import co.postscriptum.exception.BadRequestException;
import co.postscriptum.exception.ForbiddenException;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.DataFactory;
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
            log.info("Can't figure out screenName from email: {}" + email);
            return email;
        }

    }

    public UserDTO getUserDTO(UserData userData, Optional<SecretKey> userEncryptionKey) {

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

            dto.setUsedSpaceBytes(new UserDataHelper(userData).getUserUsedSpaceBytes());
            dto.setValidAesKey(userEncryptionKey.isPresent());
            dto.setNeedPayment(needPayment(userData));

        }

        dto.setUnreadNotifs(userData.getNotifications()
                                    .stream()
                                    .filter(n -> !n.isRead())
                                    .count());

        dto.setUserPlan(userData.getInternal().getUserPlan());
        dto.setTotpUri(totpHelperService.getTotpUri(userData));
        dto.setTotpRecoveryEmail(userData.getInternal().getTotpRecoveryEmail());
        dto.setEnableTotp(userData.getInternal().isEnableTotp());

        return dto;
    }

    public void deleteUser(UserData userData, String loginPassword) {
        new UserDataHelper(userData).verifyLoginPasswordIsCorrect(loginPassword);
        db.removeUserByUuid(userData.getUser().getUuid());
    }

    private boolean needPayment(UserData userData) {
        UserPlan userPlan = userData.getInternal().getUserPlan();
        return userPlan.getPaidUntil() < System.currentTimeMillis();
    }

    public void requestForStorage(UserData userData, int numberOfMb) {

        new UserDataHelper(userData).addNotification("User issued request for storage increase of " + numberOfMb + " MB");

        RequiredAction ra = DataFactory.newRequiredAction(userData, Type.USER_STORAGE_INCREASE_REQUEST);
        ra.getDetails().put("numberOfMb", "" + numberOfMb);

        adminHelperService.addAdminRequiredAction(ra);

    }

    public void changeLoginPassword(UserData userData, Optional<SecretKey> userEncryptionKey, String loginPassword, String newLoginPassword) {

        new UserDataHelper(userData).verifyLoginPasswordIsCorrect(loginPassword);

        UserInternal userInternal = userData.getInternal();

        if (!new UserDataHelper(userData).isUserAdmin()) {
            userEncryptionKey
                    .ifPresent(encryptionKey -> {
                        log.info("User encryption key is present, encrypting it by a new login password");
                        userInternal.setEncryptionKey(AESGCMUtils.encryptByPassword(newLoginPassword, encryptionKey.getEncoded()));
                    });

        }

        userInternal.setPasswordHash(PasswordUtils.hashPassword(newLoginPassword));

    }

    private String convertToTriggerValidEmails(String emails) {
        return String.join("; ", Utils.extractValidEmails(emails));
    }

    public void updateUser(UserData userData, UpdateUserDTO params) {

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

    public byte[] setEncryptionKey(UserData userData, String loginPassword, String encryptionKeyToSet) {

        new UserDataHelper(userData).verifyLoginPasswordIsCorrect(loginPassword);

        UserInternal internal = userData.getInternal();

        if (StringUtils.isEmpty(encryptionKeyToSet)) {

            log.info("Removing EncryptionKey from UserData");

            internal.setEncryptionKey(null);
            internal.setEncryptionKeyEncryptedByAdminPublicKey(null);

            return null;
        } else {

            log.info("Installing EncryptionKey in UserData");

            byte[] secretKey = Utils.base32decode(encryptionKeyToSet);

            internal.setEncryptionKey(AESGCMUtils.encryptByPassword(loginPassword, secretKey));
            internal.setEncryptionKeyEncryptedByAdminPublicKey(
                    RSAOAEPUtils.encrypt(secretKey, adminHelperService.getAdminPublicKey()));

            return secretKey;
        }

    }

    public List<String> sendUserVerificationAfterX(UserData userData, boolean sendEmailOnlyToUser) {
        return userEmailService.sendUserVerificationAfterX(userData, sendEmailOnlyToUser);
    }

    public List<String> sendUserVerificationAfterY(UserData userData, boolean sendEmailOnlyToUser) {
        return userEmailService.sendUserVerificationAfterY(userData, sendEmailOnlyToUser);
    }

    public List<String> sendUserVerificationAfterZ(UserData userData, boolean sendEmailOnlyToUser) {
        return userEmailService.sendUserVerificationAfterZ(userData, sendEmailOnlyToUser);
    }

    public void unloadUser(UserData userData) {
        try {
            db.unloadUserByUuid(userData.getUser().getUuid());
        } catch (IOException e) {
            log.error("Exception when unloading User", e);
        }
    }

    public void enable2FA(UserData userData, String totpToken) {
        log.info("Enabling 2FA");

        if (totpHelperService.isTokenValid(userData, totpToken)) {
            log.info("Provided TOTP token is correct, enabling 2FA");

            userData.getInternal().setEnableTotp(true);
        } else {
            throw new ForbiddenException("Provided token is not correct");
        }

    }

    public void disable2FA(UserData userData) {
        log.info("Disabling 2FA");
        userData.getInternal().setEnableTotp(false);
    }

    public void generateTotpSecret(UserData userData) {
        log.info("Generating TOTP Secret");
        userData.getInternal().setTotpSecret(AESKeyUtils.randomByteArray(8));
        disable2FA(userData);
    }

    public ResponseEntity<InputStreamResource> getTotpUriQr(UserData userData) {
        return totpHelperService.getTotpUriQr(userData);
    }

    public String getPaymentBitcoinAddress(UserData userData) {
        if (!needPayment(userData)) {
            throw new ForbiddenException("Can't obtain payment address for paid off account");
        }
        return bitcoinService.getPaymentForUser(userData).getBtcAddress();
    }

}
