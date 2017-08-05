package co.postscriptum.web;

import co.postscriptum.security.MyAuthenticationToken;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@UtilityClass
public class AuthenticationHelper {

    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private MyAuthenticationToken getMyAuthenticationToken() {
        return (MyAuthenticationToken) getAuthentication();
    }

    public boolean isUserLogged() {
        Authentication authentication = getAuthentication();
        return authentication != null && getAuthentication() instanceof MyAuthenticationToken;
    }

    public Optional<String> getLoggedUsername() {
        if (!isUserLogged()) {
            return Optional.empty();
        }
        return Optional.of(getMyAuthenticationToken().getName());
    }

    public String requireLoggedUsername() {
        return getLoggedUsername()
                .orElseThrow(() -> new IllegalStateException("user is not logged in"));
    }

    public boolean isUserLogged(String username) {
        return isUserLogged() && username.equals(AuthenticationHelper.requireLoggedUsername());
    }

}
