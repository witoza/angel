package co.postscriptum.model.bo;

import co.postscriptum.security.AESGCMEncryptedByPassword;
import co.postscriptum.security.RSAOAEPEncrypted;
import lombok.Data;

import java.util.List;

@Data
public class UserInternal {

    UserPlan userPlan;
    Lang lang;

    List<Long> invalidLoginTs;

    long accountLockedUntil;

    long creationTime;
    String screenName;
    long quotaBytes;

    String passwordHash;
    boolean enableTotp;

    boolean allowPasswordReset;
    boolean verifyUnknownBrowsers;

    byte[] totpSecret;
    String totpRecoveryEmail;

    List<LoginAttempt> loginHistory;

    // user only
    TriggerInternal triggerInternal;
    AESGCMEncryptedByPassword encryptionKey;
    RSAOAEPEncrypted encryptionKeyEncryptedByAdminPublicKey;

}
