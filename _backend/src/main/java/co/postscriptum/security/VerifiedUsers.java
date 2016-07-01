package co.postscriptum.security;

import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.UserData;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VerifiedUsers {

    private static final String VERIFIED_USERS_COOKIE_NAME = "VERIFIED_USERS";

    private final String salt;
    private final List<String> verified;

    public VerifiedUsers(HttpServletRequest request, String salt) {
        this.salt = salt;
        this.verified = new ArrayList<>(
                Arrays.stream(
                        Utils.getCookieValue(request, VERIFIED_USERS_COOKIE_NAME)
                             .orElse("")
                             .split("\\|"))
                      .filter(vu -> vu.length() > 10)
                      .collect(Collectors.toList()));
    }

    private String userHash(UserData userData) {
        return Hex.encodeHexString(DigestUtils.sha256(salt + userData.getUser().getUuid()));
    }

    public boolean isUserVerified(UserData userData) {
        return verified.contains(userHash(userData));
    }

    public void markAsVerified(UserData userData) {
        if (!isUserVerified(userData)) {
            verified.add(userHash(userData));
        }
    }

    public String toCookieValue() {
        return String.join("|", verified);
    }

    public Cookie toCookie() {
        Cookie cookie = new Cookie(VERIFIED_USERS_COOKIE_NAME, toCookieValue());
        cookie.setMaxAge(10 * 365 * 24 * 60 * 60);
        return cookie;
    }

    public void updateCookie(HttpServletResponse response) {
        response.addCookie(toCookie());
    }

}
