package co.postscriptum.controller;

import co.postscriptum.controller.dto.PasswordDTO;
import co.postscriptum.exception.BadRequestException;
import co.postscriptum.exception.ForbiddenException;
import co.postscriptum.service.AdminHelperService;
import co.postscriptum.internal.MyConfiguration;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.Lang;
import co.postscriptum.security.MyAuthenticationToken;
import co.postscriptum.security.RequestMetadata;
import co.postscriptum.security.VerifiedUsers;
import co.postscriptum.service.CaptchaService;
import co.postscriptum.service.LoginService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/")
@AllArgsConstructor
public class LoginController {

    private final MyConfiguration myConfiguration;

    private final CookieCsrfTokenRepository cookieCsrfTokenRepository;

    private final LoginService loginService;

    private final CaptchaService captchaService;

    private final AdminHelperService adminHelperService;

    @PostMapping("/preregister")
    public void preregister(@Valid @RequestBody PreregisterDTO dto, HttpServletRequest request) {
        loginService.preregister(dto.username, dto.lang, new RequestMetadata(request));
    }

    private void generateNewCSRFToken(HttpServletRequest request, HttpServletResponse response) {
        cookieCsrfTokenRepository.saveToken(cookieCsrfTokenRepository.generateToken(request), request, response);
    }

    @PostMapping("/register")
    public void register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request, HttpServletResponse response) {

        VerifiedUsers verifiedUsers = new VerifiedUsers(request, myConfiguration.getVerifiedUsersSalt());

        try {
            Authentication authentication = loginService.register(dto.shortTimeKey,
                                                                  dto.passwd,
                                                                  dto.lang,
                                                                  new RequestMetadata(request),
                                                                  verifiedUsers);

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (ForbiddenException | BadRequestException e) {
            throw new ForbiddenException("Can't register user", e);
        } catch (Exception e) {
            throw new InternalError("Can't register user", e);
        }

        verifiedUsers.updateCookie(response);

        generateNewCSRFToken(request, response);

    }

    @PostMapping("/login")
    public void login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request, HttpServletResponse response) {

        VerifiedUsers verifiedUsers = new VerifiedUsers(request, myConfiguration.getVerifiedUsersSalt());

        try {
            Authentication authentication = loginService.login(dto.username,
                                                               dto.passwd,
                                                               dto.totpToken,
                                                               dto.loginToken,
                                                               new RequestMetadata(request),
                                                               verifiedUsers);

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (ForbiddenException | BadRequestException e) {
            if (e.getMessage().contains("token")) {
                throw e;
            }
            throw new ForbiddenException("Invalid username or password", e);
        } catch (Exception e) {
            throw new ForbiddenException("Invalid username or password", e);
        }

        verifiedUsers.updateCookie(response);

        generateNewCSRFToken(request, response);
    }

    private boolean isUserLogged() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof MyAuthenticationToken;
    }

    @PostMapping("/send_message")
    public void sendMessage(@Valid @RequestBody SendMessageDTO dto, HttpServletRequest request) {

        if (!isUserLogged()) {
            if (!captchaService.verify(dto.getMyRecaptchaResponse())) {
                throw new BadRequestException("To send us a message you have to solve captcha");
            }
        }

        adminHelperService.sendToAdminContactForm(dto.getFrom(),
                                                  dto.getTitle(),
                                                  dto.getContent(),
                                                  new RequestMetadata(request));

    }

    @PostMapping("/recall_totp_secret")
    public Map<String, String> recallTotpSecret(@Valid @RequestBody UsernamePasswordDTO dto) {
        try {
            return Collections.singletonMap("emailSentTo", loginService.recallTotpKey(dto.username, dto.passwd));
        } catch (Exception e) {
            throw new ForbiddenException("Invalid parameters", e);
        }
    }

    @GetMapping("/recall_totp_details_qr")
    public ResponseEntity<InputStreamResource> recallTotpDetailsQr(@RequestParam("key") String key) {
        try {
            return loginService.recallTotpKeyDetailsQr(key);
        } catch (Exception e) {
            throw new ForbiddenException("Invalid parameters", e);
        }
    }

    @GetMapping(value = "/recall_totp_details_info", produces = "text/html; charset=utf-8")
    @ResponseBody
    public String recallTotpDetailsInfo(@RequestParam("key") String key) {
        try {
            return loginService.recallTotpKeyDetailsInfo(key);
        } catch (Exception e) {
            throw new ForbiddenException("Invalid parameters", e);
        }
    }

    @PostMapping("/change_passwd_by_reset_key")
    public void changePasswordByResetKey(@Valid @RequestBody ChangePasswordByResetKeyDTO dto) {
        try {
            loginService.changePasswordByResetKey(dto.reset_passwd_key, dto.secret, dto.passwd_new);
        } catch (Exception e) {
            throw new ForbiddenException("Invalid parameters", e);
        }
    }

    @PostMapping("/reset_passwd")
    public void resetPassword(@Valid @RequestBody UsernameDTO dto) {
        try {
            loginService.resetPassword(dto.username);
        } catch (IllegalArgumentException | ForbiddenException e) {
            log.warn("problem while resetting password: {}", Utils.exceptionInfo(e));
        } catch (Exception e) {
            log.error("error while resetting password", e);
        }
    }

    @GetMapping(value = "/alive", produces = "text/html; charset=utf-8")
    @ResponseBody
    public String alive(@RequestParam("user_uuid") String userUuid, @RequestParam("key") String key) {
        try {
            return loginService.alive(userUuid, key);
        } catch (Exception e) {
            throw new ForbiddenException("Invalid parameters", e);
        }
    }

    @Getter
    @Setter
    public static class UsernameDTO {

        @Email
        @NotEmpty
        @Size(max = 50)
        public String username;

    }

    @Getter
    @Setter
    public static class UsernamePasswordDTO extends UsernameDTO {

        @NotEmpty
        @Size(min = 3, max = 20)
        public String passwd;

    }


    @Getter
    @Setter
    public static class PreregisterDTO extends UsernameDTO {

        @NotNull
        Lang lang;

    }

    @Getter
    @Setter
    public static class RegisterDTO extends PasswordDTO {

        @NotEmpty
        @Size(min = 32, max = 32)
        String shortTimeKey;

        @NotNull
        Lang lang;

    }

    @Getter
    @Setter
    public static class LoginDTO extends UsernamePasswordDTO {

        @Size(min = 6, max = 6)
        String totpToken;

        @Size(min = 32, max = 32)
        String loginToken;
    }

    @Getter
    @Setter
    public static class SendMessageDTO {

        @NotEmpty
        @Email
        @Size(max = 50)
        String from;

        @NotEmpty
        @Size(max = 50)
        String title;

        @NotEmpty
        @Size(max = 1000)
        String content;

        @Size(min = 300, max = 400)
        String myRecaptchaResponse;

    }

    @Getter
    @Setter
    public static class ChangePasswordByResetKeyDTO {

        @NotEmpty
        @Size(min = 32, max = 32)
        String reset_passwd_key;

        @NotEmpty
        @Size(max = 80)
        String secret;

        @NotEmpty
        @Size(min = 3, max = 20)
        String passwd_new;
    }

}
