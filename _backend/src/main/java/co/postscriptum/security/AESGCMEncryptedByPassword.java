package co.postscriptum.security;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AESGCMEncryptedByPassword {

    private byte[] passwordSalt;

    private AESGCMEncrypted encrypted;

}
