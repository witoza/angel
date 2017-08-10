package co.postscriptum.security;

import co.postscriptum.internal.Utils;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AESGCMEncrypted {

    private byte[] ct;

    private byte[] iv;

    public static AESGCMEncrypted fromString(String value) {
        String parts[] = value.split("\\|");
        return new AESGCMEncrypted(Utils.base64decode(parts[0]), Utils.base64decode(parts[1]));
    }

    public String toString() {
        return Utils.base64encode(ct) + "|" + Utils.base64encode(iv);
    }

}