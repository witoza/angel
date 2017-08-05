package co.postscriptum.security;

import org.springframework.security.crypto.bcrypt.BCrypt;

public class PasswordUtils {

    public static String hashPassword(String loginPassword) {
        return BCrypt.hashpw(loginPassword, BCrypt.gensalt(11));
    }

    public static boolean checkPasswordHash(String loginPassword, String passwordHash) {
        return BCrypt.checkpw(loginPassword, passwordHash);
    }

}
