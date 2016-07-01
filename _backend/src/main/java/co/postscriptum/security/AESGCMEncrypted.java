package co.postscriptum.security;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AESGCMEncrypted {

    private final byte[] ct;
    private final byte[] iv;

}