package co.postscriptum.security;

import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.UserData;
import com.google.common.base.Splitter;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;

public class VerifiedUsers {

    private static final String VERIFIED_USERS_COOKIE_NAME = "VERIFIED_USERS";

    private final String salt;

    private final List<String> verified;

    public VerifiedUsers(HttpServletRequest request, String salt) {
        this.salt = salt;
        this.verified = Utils.getCookieValue(request, VERIFIED_USERS_COOKIE_NAME)
                             .map(cookieValue -> Splitter.on('|').splitToList(cookieValue))
                             .orElse(Collections.emptyList());
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
        cookie.setSecure(true);
        cookie.setMaxAge(10 * 365 * 24 * 60 * 60);
        return cookie;
    }

    public void updateCookie(HttpServletResponse response) {
        response.addCookie(toCookie());
    }

}
