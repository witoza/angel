package co.postscriptum.security;

import co.postscriptum.exception.ExceptionBuilder;
import co.postscriptum.internal.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.SecretKey;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

@Slf4j
@Component
public class UserEncryptionKeyService {

    private static final String ENCRYPTION_KEY_COOKIE = "ENCRYPTION_KEY";

    private static final String SET_ENCRYPTION_KEY = UserEncryptionKeyService.class.getSimpleName() + ".SetEncryptionKey";

    private final SecretKey cookieEncryptionKey = AESKeyUtils.generateRandomKey();

    public Optional<SecretKey> getEncryptionKey(HttpServletRequest httpServletRequest) {
        return loadEncryptionKey(httpServletRequest)
                .map(bytes -> {
                    try {
                        return AESKeyUtils.toSecretKey(bytes);
                    } catch (Exception e) {
                        return null;
                    }
                });
    }

    public Optional<SecretKey> getEncryptionKey() {
        return getEncryptionKey(request());
    }

    public SecretKey requireEncryptionKey(HttpServletRequest httpServletRequest) {
        return getEncryptionKey(httpServletRequest)
                .orElseThrow(ExceptionBuilder.badRequest("Missing Encryption Key"));
    }

    public void setEncryptionKey(HttpServletRequest httpServletRequest, byte[] encryptionKey) {
        httpServletRequest.setAttribute(SET_ENCRYPTION_KEY, Optional.ofNullable(encryptionKey));
    }

    private Optional<byte[]> loadEncryptionKey(HttpServletRequest httpServletRequest) {
        log.info("Loading EncryptionKey from cookie: {}", ENCRYPTION_KEY_COOKIE);
        return Utils.getCookieValue(httpServletRequest, ENCRYPTION_KEY_COOKIE)
                    .map(cookieValue -> {
                        try {
                            String parts[] = cookieValue.split("\\|");
                            return AESGCMUtils.decrypt(cookieEncryptionKey,
                                                       new AESGCMEncrypted(Utils.base64decode(parts[0]), Utils.base64decode(parts[1])));
                        } catch (Exception e) {
                            log.warn("Invalid cookie: {} value: {}", ENCRYPTION_KEY_COOKIE, cookieValue);
                            return null;
                        }
                    });
    }

    public void persistEncryptionKey(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        Optional<byte[]> encryptionKeyToSet = (Optional<byte[]>) httpServletRequest.getAttribute(SET_ENCRYPTION_KEY);
        if (encryptionKeyToSet != null) {
            log.info("Set cookie: {}", ENCRYPTION_KEY_COOKIE);
            Cookie cookie = toCookie(encryptionKeyToSet.orElse(null));
            httpServletResponse.addCookie(cookie);
        }
    }

    public void resetEncryptionKeyCookie(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        Utils.getCookieValue(httpServletRequest, ENCRYPTION_KEY_COOKIE)
             .ifPresent(secretKey -> {
                 log.info("Resetting Cookie: {}", ENCRYPTION_KEY_COOKIE);
                 httpServletResponse.addCookie(toCookie(null));
             });
    }

    private Cookie toCookie(byte[] value) {
        Cookie cookie;
        if (value == null) {
            cookie = new Cookie(ENCRYPTION_KEY_COOKIE, null);
            cookie.setMaxAge(0);
        } else {
            AESGCMEncrypted encrypted = AESGCMUtils.encrypt(cookieEncryptionKey, value);
            cookie = new Cookie(ENCRYPTION_KEY_COOKIE, Utils.base64encode(encrypted.getCt()) + "|" + Utils.base64encode(encrypted.getIv()));

            // 1 day
            cookie.setMaxAge(24 * 60 * 60);
        }
        cookie.setPath("/");
        return cookie;
    }

    public SecretKey requireEncryptionKey() {
        return requireEncryptionKey(request());
    }

    public void setEncryptionKey(byte[] encryptionKey) {
        setEncryptionKey(request(), encryptionKey);
    }

    private HttpServletRequest request() {
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }

}
