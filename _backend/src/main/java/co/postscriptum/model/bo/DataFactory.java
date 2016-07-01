package co.postscriptum.model.bo;

import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.RequiredAction.Status;
import co.postscriptum.model.bo.RequiredAction.Type;
import co.postscriptum.model.bo.Trigger.Stage;
import co.postscriptum.model.bo.User.Role;
import co.postscriptum.model.bo.UserPlan.Payment;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import co.postscriptum.security.PasswordUtils;
import co.postscriptum.security.RSAOAEPUtils;

import java.security.PublicKey;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;

public class DataFactory {

    public static File cloneBasicData(File file) {
        return File.builder()
                   .name(file.getName())
                   .mime(file.getMime())
                   .ext(file.getExt())
                   .uploadTime(file.getUploadTime())
                   .size(file.getSize())
                   .sha1(file.getSha1())
                   .iv(file.getIv())
                   .build();
    }

    public static User newUser() {
        return User.builder()
                   .uuid(Utils.randKey("U"))
                   .role(Role.user)
                   .trigger(Trigger.builder()
                                   .enabled(false)
                                   .timeUnit(ChronoUnit.DAYS)
                                   .x(30)
                                   .y(10)
                                   .z(10)
                                   .w(10)
                                   .stage(Stage.beforeX)
                                   .build())
                   .build();
    }

    public static Message newMessage() {
        Message message = new Message();
        message.setUuid(Utils.randKey("M"));
        message.setCreationTime(System.currentTimeMillis());
        message.setUpdateTime(System.currentTimeMillis());
        message.setAttachments(new ArrayList<>());
        message.setRecipients(new ArrayList<>());
        return message;
    }

    private static UserInternal newUserInternal(String loginPassword) {
        UserInternal internal = new UserInternal();

        internal.setLang(Lang.en);
        internal.setPasswordHash(PasswordUtils.hashPassword(loginPassword));
        internal.setLoginHistory(new ArrayList<>());
        internal.setTotpSecret(AESKeyUtils.randomByteArray(8));
        internal.setEnableTotp(false);
        internal.setCreationTime(System.currentTimeMillis());
        internal.setInvalidLoginTs(new ArrayList<>());
        internal.setVerifyUnknownBrowsers(true);

        return internal;
    }

    public static UserInternal newAdminUserInternal(String loginPassword) {
        UserInternal internal = newUserInternal(loginPassword);
        internal.setAllowPasswordReset(false);
        return internal;
    }

    public static UserInternal newUserInternal(String loginPassword, PublicKey adminPublicKey) {
        UserInternal internal = newUserInternal(loginPassword);

        UserPlan userPlan = new UserPlan();
        userPlan.setPaidUntil(System.currentTimeMillis() + Utils.daysInMs(30));
        userPlan.setPayments(new ArrayList<>());
        userPlan.getPayments().add(Payment.builder()
                                          .time(System.currentTimeMillis())
                                          .amount("NA")
                                          .details("Account is valid for 30 days free of charge")
                                          .build());

        internal.setUserPlan(userPlan);

        byte[] encryptionKey = AESKeyUtils.generateRandomKey().getEncoded();
        internal.setEncryptionKey(AESGCMUtils.encryptByPassword(loginPassword, encryptionKey));
        internal.setEncryptionKeyEncryptedByAdminPublicKey(RSAOAEPUtils.encrypt(encryptionKey, adminPublicKey));

        TriggerInternal triggerInternal = new TriggerInternal();
        triggerInternal.setResetKey(Utils.randKey("RK"));
        triggerInternal.setXemails("");
        triggerInternal.setYemails("");
        triggerInternal.setZemails("");
        internal.setTriggerInternal(triggerInternal);

        internal.setAllowPasswordReset(true);

        return internal;
    }

    public static RequiredAction newRequiredAction(UserData userData, Type type) {
        RequiredAction requiredAction = new RequiredAction();
        requiredAction.setType(type);
        requiredAction.setCreatedTime(System.currentTimeMillis());
        requiredAction.setUuid(Utils.randKey("RA"));
        requiredAction.setDetails(new HashMap<>());
        requiredAction.setStatus(Status.unresolved);
        requiredAction.setResolutions(new ArrayList<>());
        requiredAction.setUserUuid(userData.getUser().getUuid());
        requiredAction.setUserUsername(userData.getUser().getUsername());
        return requiredAction;
    }

    public static ReleaseItem.Reminder newReminder() {
        ReleaseItem.Reminder remainder = new ReleaseItem.Reminder();
        remainder.setUuid(Utils.randKey("REM"));
        remainder.setResolved(false);
        remainder.setCreatedTime(System.currentTimeMillis());
        return remainder;
    }

    public static UserData newUserData() {
        UserData userData = new UserData();
        userData.setFiles(new ArrayList<>());
        userData.setMessages(new ArrayList<>());
        userData.setNotifications(new ArrayList<>());
        userData.setRequiredActions(new ArrayList<>());
        return userData;
    }
}
