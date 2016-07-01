package co.postscriptum.stk;

import co.postscriptum.security.AESGCMEncrypted;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShortTimeKey {

    private final String username;
    private final String key;
    private final Type type;

    private long validUntil;
    private AESGCMEncrypted extraData;

    public enum Type {
        password_change_req,
        recall_otp_key,
        register_user,
        login
    }

}
