package co.postscriptum.security;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RSAOAEPEncrypted {

    private byte[] ct;

}
