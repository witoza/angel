package co.postscriptum.security;

import co.postscriptum.model.bo.UserInternal;
import org.springframework.security.crypto.bcrypt.BCrypt;

public class PasswordUtils {

    public static String hashPassword(String loginPassword) {
        return BCrypt.hashpw(loginPassword, BCrypt.gensalt(11));
    }

    public static boolean checkPasswordHash(String loginPassword, UserInternal userInternal) {
        return BCrypt.checkpw(loginPassword, userInternal.getPasswordHash());
    }

}
