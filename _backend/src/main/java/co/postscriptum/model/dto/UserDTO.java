package co.postscriptum.model.dto;

import co.postscriptum.model.bo.Lang;
import co.postscriptum.model.bo.Trigger;
import co.postscriptum.model.bo.TriggerInternal;
import co.postscriptum.model.bo.User.Role;
import co.postscriptum.model.bo.UserPlan;
import lombok.Data;

@Data
public class UserDTO {

    String uuid;

    boolean active;
    long creationTime;
    boolean tosAccepted;
    long quotaBytes;
    String username;
    String screenName;
    Lang lang;
    Role role;
    Trigger trigger;
    TriggerInternal triggerInternal;

    boolean allowPasswordReset;
    boolean verifyUnknownBrowsers;

    long usedSpaceBytes;
    boolean validAesKey;
    long unreadNotifs;
    boolean needPayment;

    String totpUri;
    boolean enableTotp;
    String totpRecoveryEmail;

    UserPlan userPlan;

}
