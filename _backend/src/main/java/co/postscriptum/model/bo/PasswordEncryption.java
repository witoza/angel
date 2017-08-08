package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PasswordEncryption {

    private final String hint;

    private final byte[] salt;

    private byte[] iv;

}
