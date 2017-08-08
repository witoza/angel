package co.postscriptum.model.dto;

import co.postscriptum.model.bo.Lang;
import co.postscriptum.model.bo.Trigger;
import co.postscriptum.model.bo.TriggerInternal;
import co.postscriptum.model.bo.User.Role;
import co.postscriptum.model.bo.UserPlan;
import lombok.Data;

@Data
public class UserDTO {

    private String uuid;

    private boolean active;

    private long creationTime;

    private boolean tosAccepted;

    private long quotaBytes;

    private String username;

    private String screenName;

    private Lang lang;

    private Role role;

    private Trigger trigger;

    private TriggerInternal triggerInternal;

    private boolean allowPasswordReset;

    private boolean verifyUnknownBrowsers;

    private long usedSpaceBytes;

    private boolean validAesKey;

    private long unreadNotifs;

    private boolean needPayment;

    private String totpUri;

    private boolean enableTotp;

    private String totpRecoveryEmail;

    private UserPlan userPlan;

}
