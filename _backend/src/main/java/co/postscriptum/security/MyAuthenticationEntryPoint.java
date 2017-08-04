package co.postscriptum.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
public final class MyAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Autowired
    private CookieCsrfTokenRepository cookieCsrfTokenRepository;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {

        log.info("Commencing login, creating session & CSRF cookie, sending 401");

        request.getSession(true);

        cookieCsrfTokenRepository.saveToken(cookieCsrfTokenRepository.generateToken(request), request, response);

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

}