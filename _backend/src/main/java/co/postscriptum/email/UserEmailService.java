package co.postscriptum.email;

import co.postscriptum.exception.BadRequestException;
import co.postscriptum.internal.Utils;
import co.postscriptum.job.EmailProcessor;
import co.postscriptum.model.bo.Lang;
import co.postscriptum.model.bo.TriggerInternal;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.bo.UserInternal;
import co.postscriptum.security.RequestMetadata;
import co.postscriptum.security.TOTPHelperService;
import co.postscriptum.service.EnvelopeCreatorService;
import co.postscriptum.stk.ShortTimeKey;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@AllArgsConstructor
public class UserEmailService {

    private final EnvelopeCreatorService envelopeCreatorService;

    private final TOTPHelperService totpHelperService;

    private final EmailProcessor emailProcessor;

    public List<String> sendUserVerificationAfterX(UserData userData, boolean sendOnlyToAccountOwner) {
        List<String> recipients;
        if (sendOnlyToAccountOwner) {
            recipients = Collections.singletonList(userData.getUser().getUsername());
        } else {
            TriggerInternal trigger = userData.getInternal().getTriggerInternal();
            recipients = Utils.extractValidEmails(trigger.getXemails());
        }

        sendEmailsTo(EnvelopeType.TRIGGER_AFTER_X, userData, recipients, "verify_user_alive_X");
        return recipients;
    }

    public List<String> sendUserVerificationAfterY(UserData userData, boolean sendOnlyToAccountOwner) {
        List<String> recipients;
        if (sendOnlyToAccountOwner) {
            recipients = Collections.singletonList(userData.getUser().getUsername());
        } else {
            TriggerInternal trigger = userData.getInternal().getTriggerInternal();
            recipients = Utils.extractValidEmails(trigger.getXemails(), trigger.getYemails());
        }

        sendEmailsTo(EnvelopeType.TRIGGER_AFTER_Y, userData, recipients, "verify_user_alive_XY");
        return recipients;
    }

    public List<String> sendUserVerificationAfterZ(UserData userData, boolean sendOnlyToAccountOwner) {
        List<String> recipients;
        if (sendOnlyToAccountOwner) {
            recipients = Collections.singletonList(userData.getUser().getUsername());
        } else {
            TriggerInternal trigger = userData.getInternal().getTriggerInternal();
            recipients = Utils.extractValidEmails(trigger.getXemails(), trigger.getYemails(), trigger.getZemails());
        }

        sendEmailsTo(EnvelopeType.TRIGGER_AFTER_Z, userData, recipients, "verify_user_alive_XYZ");
        return recipients;
    }

    public String sendToOwnerPreregisters(String username, Lang lang, ShortTimeKey shortTimeKey) {

        Map<String, Object> context = new HashMap<>();
        context.put("shortTimeKey", shortTimeKey.getKey());

        User user = User.builder()
                        .username(username)
                        .uuid("")
                        .build();

        UserInternal internal = new UserInternal();
        internal.setLang(lang);

        UserData userData = new UserData();
        userData.setUser(user);
        userData.setInternal(internal);

        return sendEmailTo(EnvelopeType.USER_PREREGISTER_NOT_EXIST, userData, username, "register_new_user", context);
    }

    public String sendToOwnerMessagesAreAboutToBeReleased(UserData userData) {
        return sendEmailToUser(EnvelopeType.MESSAGES_ABOUT_TO_BE_RELEASED, userData, "messages_are_about_to_send");
    }

    public String sendToOwnerAccountAlreadyExists(UserData userData) {
        return sendEmailToUser(EnvelopeType.USER_PREREGISTER_EXIST, userData, "account_already_exists");
    }

    public String sendToOwnerRequestedPasswordReset(UserData userData) {
        return sendEmailToUser(EnvelopeType.USER_RESET_PASSWORD, userData, "user_reset_passwd_req");
    }

    public String sendToOwnerAdminApprovedRequestPasswordReset(UserData userData, ShortTimeKey stk, SecretKey tmpKey) {

        Map<String, Object> context = new HashMap<>();
        context.put("shortTimeKey", stk.getKey());
        if (tmpKey != null) {
            context.put("secret", Utils.urlEncode(Utils.base64encode(tmpKey.getEncoded())));
        }

        return sendEmailTo(EnvelopeType.USER_PASSWORD_CHANGE_APPROVED_BY_ADMIN,
                           userData,
                           userData.getUser().getUsername(),
                           "admin_approves_password_change",
                           context);
    }


    public String sendToOwnerTooManyInvalidLoginAttempts(UserData userData) {
        return sendEmailToUser(EnvelopeType.USER_INVALID_3_LOGINS_ACCOUNT_LOCKED, userData, "too_many_invalid_logins");
    }

    public String sendToOwnerBrowserRequiresVerification(UserData userData, ShortTimeKey stk, RequestMetadata requestMetadata) {
        Map<String, Object> context = new HashMap<>();
        context.put("shortTimeKey", stk.getKey());
        context.put("webbrowser_details", requestMetadata.getRequestDetails());
        return sendEmailTo(EnvelopeType.USER_LOGIN_VERIFICATION,
                           userData,
                           userData.getUser().getUsername(),
                           "sent_login_verification",
                           context);
    }

    public String sendToOwnerTOTPTokenDetails(UserData userData, ShortTimeKey stk) {
        String recoveryEmail = userData.getInternal().getTotpRecoveryEmail();
        if (StringUtils.isEmpty(recoveryEmail)) {
            throw new BadRequestException("token recovery email is not defined");
        }
        if (!Utils.isValidEmail(recoveryEmail)) {
            throw new BadRequestException("token recovery email is not valid");
        }

        Map<String, Object> context = new HashMap<>();
        context.put("totpUri", totpHelperService.getTotpUri(userData));
        context.put("shortTimeKey", stk.getKey());

        return sendEmailTo(EnvelopeType.USER_TOTP_DETAILS, userData, recoveryEmail, "sent_totp_details", context);

    }

    private String sendEmailToUser(EnvelopeType envelopeType, UserData userData, String templateKey) {
        return sendEmailTo(envelopeType, userData, userData.getUser().getUsername(), templateKey, new HashMap<>());
    }

    private String sendEmailTo(EnvelopeType envelopeType,
                               UserData userData,
                               String recipient,
                               String templateKey,
                               Map<String, Object> context) {

        Envelope envelope = envelopeCreatorService.create(envelopeType, userData, recipient, templateKey, context);

        emailProcessor.enqueue(envelope);

        return envelope.getEnvelopeId();
    }

    private void sendEmailsTo(EnvelopeType envelopeType, UserData userData, List<String> recipients, String template) {
        recipients.forEach(recipient -> {
            sendEmailTo(envelopeType, userData, recipient, template, new HashMap<>());
        });
    }

}
