package co.postscriptum.email;

import lombok.Getter;

@Getter
public enum EnvelopeType {

    SYSTEM_TO_ADMIN(true, false, false),
    CONTACT_FORM(true, false, false),

    TRIGGER_AFTER_X(true, false, true),
    TRIGGER_AFTER_Y(true, false, true),
    TRIGGER_AFTER_Z(true, false, true),
    MESSAGES_ABOUT_TO_BE_RELEASED(true, false, false),
    RELEASE_SUMMARY(true, false, false),
    RELEASE_ITEM(true, true, true),
    RELEASE_ITEM_TEST(false, false, false),

    USER_PREREGISTER_NOT_EXIST(false, false, false),
    USER_PREREGISTER_EXIST(false, false, false),
    USER_RESET_PASSWORD(false, false, false),
    USER_TOTP_DETAILS(false, false, false),
    USER_LOGIN_VERIFICATION(false, false, false),
    USER_PASSWORD_CHANGE_APPROVED_BY_ADMIN(false, false, false),
    USER_INVALID_3_LOGINS_ACCOUNT_LOCKED(false, false, false);

    //should email be stored on disk
    private final boolean durable;

    // should user get report about success/fail of sending that message
    private final boolean reportSuccess;

    private final boolean reportFailure;

    EnvelopeType(boolean durable, boolean reportSuccess, boolean reportFailure) {
        this.durable = durable;
        this.reportSuccess = reportSuccess;
        this.reportFailure = reportFailure;
    }

    public static EnvelopeType fromEnvelopeId(String envelopeId) {
        String[] parts = envelopeId.split("#");
        return EnvelopeType.valueOf(parts[0]);
    }

}
