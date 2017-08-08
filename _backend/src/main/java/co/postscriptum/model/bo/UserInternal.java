package co.postscriptum.model.bo;

import co.postscriptum.security.AESGCMEncryptedByPassword;
import co.postscriptum.security.RSAOAEPEncrypted;
import lombok.Data;

import java.util.List;

@Data
public class UserInternal {

    private Lang lang;

    private List<Long> invalidLoginTs;

    private long accountLockedUntil;

    private long creationTime;

    private String screenName;

    private long quotaBytes;

    private String passwordHash;

    private boolean enableTotp;

    private boolean allowPasswordReset;

    private boolean verifyUnknownBrowsers;

    private byte[] totpSecret;

    private String totpRecoveryEmail;

    private List<LoginAttempt> loginHistory;

    // user only, for admin those are null
    private UserPlan userPlan;

    private TriggerInternal triggerInternal;

    private AESGCMEncryptedByPassword encryptionKey;

    private RSAOAEPEncrypted encryptionKeyEncryptedByAdminPublicKey;

}
