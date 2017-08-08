package co.postscriptum.security;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AESGCMEncrypted {

    private byte[] ct;

    private byte[] iv;

}