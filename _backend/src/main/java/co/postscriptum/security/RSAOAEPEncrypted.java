package co.postscriptum.security;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RSAOAEPEncrypted {

    private final byte[] ct;

}
