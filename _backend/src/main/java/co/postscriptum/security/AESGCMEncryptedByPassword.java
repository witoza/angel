package co.postscriptum.security;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AESGCMEncryptedByPassword {

    private final byte[] passwordSalt;
    private final AESGCMEncrypted encrypted;

}
