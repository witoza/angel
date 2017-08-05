package co.postscriptum.security;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
@AllArgsConstructor
public final class MyAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final CookieCsrfTokenRepository cookieCsrfTokenRepository;

    private final UserEncryptionKeyService userEncryptionKeyService;

    @Override

    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {

        log.info("Commencing login, creating session & CSRF cookie, sending 401");

        request.getSession(true);

        userEncryptionKeyService.resetEncryptionKeyCookie(request, response);

        cookieCsrfTokenRepository.saveToken(cookieCsrfTokenRepository.generateToken(request), request, response);

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

}