package co.postscriptum.web;

import co.postscriptum.exception.ExceptionBuilder;
import co.postscriptum.security.AESKeyUtils;
import co.postscriptum.security.MyAuthenticationToken;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.util.Optional;

@UtilityClass
public class AuthHelper {

    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private MyAuthenticationToken getMyAuthenticationToken() {
        return (MyAuthenticationToken) getAuthentication();
    }

    public boolean isUserLogged() {
        return getAuthentication() instanceof MyAuthenticationToken;
    }

    public Optional<String> getLoggedUsername() {
        if (getAuthentication() == null) {
            return Optional.empty();
        }
        return Optional.of(getMyAuthenticationToken().getName());
    }

    public String requireLoggedUsername() {
        return getLoggedUsername()
                .orElseThrow(() -> new IllegalStateException("user is not logged in"));
    }

    public boolean isUserLogged(String username) {
        return isUserLogged() && username.equals(AuthHelper.requireLoggedUsername());
    }

    public Optional<SecretKey> getUserEncryptionKey() {
        return AuthHelper.getMyAuthenticationToken()
                         .getKey()
                         .map(AESKeyUtils::toSecretKey);
    }

    public SecretKey requireUserEncryptionKey() {
        return getUserEncryptionKey()
                .orElseThrow(ExceptionBuilder.badRequest("missing user encryption key"));
    }

    public void setUserEncryptionKey(byte[] encryptionKey) {
        getMyAuthenticationToken().setKey(encryptionKey);
    }

}
