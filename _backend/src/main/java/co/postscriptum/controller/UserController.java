package co.postscriptum.controller;

import co.postscriptum.controller.dto.PasswordDTO;
import co.postscriptum.exception.BadRequestException;
import co.postscriptum.internal.QRGenerator;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.Lang;
import co.postscriptum.model.bo.LoginAttempt;
import co.postscriptum.model.bo.Trigger;
import co.postscriptum.model.bo.TriggerInternal;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.dto.UserDTO;
import co.postscriptum.security.UserEncryptionKeyService;
import co.postscriptum.service.UserService;
import co.postscriptum.web.AuthenticationHelper;
import co.postscriptum.web.ResponseEntityUtils;
import com.google.zxing.WriterException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@AllArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    private final UserEncryptionKeyService userEncryptionKeyService;

    @GetMapping("/current")
    public UserDTO current(UserData userData) {
        return userService.getUserDTO(userData, userEncryptionKeyService.getEncryptionKey());
    }

    @PostMapping("/logout")
    public void logout(UserData userData, HttpServletRequest request, HttpServletResponse response) {
        if (AuthenticationHelper.getAuthentication() == null) {
            throw new BadRequestException("user already logged out");
        }
        userService.unloadUser(userData);
        userEncryptionKeyService.setEncryptionKey(null);
        new SecurityContextLogoutHandler().logout(request, response, null);
    }

    @PostMapping("/send_x_notification")
    public List<String> sendTriggerAfterX(UserData userData, @Valid @RequestBody InvokeTriggerStageDTO dto) {
        return userService.sendUserVerificationAfterX(userData, dto.getSendEmailOnlyToUser());
    }

    @PostMapping("/send_y_notification")
    public List<String> sendTriggerAfterY(UserData userData, @Valid @RequestBody InvokeTriggerStageDTO dto) {
        return userService.sendUserVerificationAfterY(userData, dto.getSendEmailOnlyToUser());
    }

    @PostMapping("/send_z_notification")
    public List<String> sendTriggerAfterZ(UserData userData, @Valid @RequestBody InvokeTriggerStageDTO dto) {
        return userService.sendUserVerificationAfterZ(userData, dto.getSendEmailOnlyToUser());
    }

    @PostMapping("/generate_totp_secret")
    public void generateTotpSecret(UserData userData) {
        userService.generateTotpSecret(userData);
    }

    @GetMapping("/totpQR")
    public ResponseEntity<InputStreamResource> totpQr(UserData userData) throws IOException, WriterException {
        return ResponseEntityUtils.asPng(userService.getTotpUriQr(userData));
    }

    @PostMapping("/get_login_history")
    public List<LoginAttempt> getLoginHistory(UserData userData) {
        return userData.getInternal().getLoginHistory();
    }

    @GetMapping("/get_qr")
    public ResponseEntity<InputStreamResource> getQr(@RequestParam("data") String data) throws WriterException, IOException {
        return ResponseEntityUtils.asPng(QRGenerator.createQr(data));
    }

    @PostMapping("/get_bitcoin_address")
    public Map<String, String> getBitcoinAddress(UserData userData) {
        return Collections.singletonMap("address", userService.getPaymentBitcoinAddress(userData));
    }

    @PostMapping("/get_aes_key")
    public Map<String, String> getAesKey() {

        String aesKey = userEncryptionKeyService.getEncryptionKey()
                                                .map(key -> Utils.base32encode(key.getEncoded()))
                                                .orElse(null);

        return Collections.singletonMap("aes_key", aesKey);
    }

    @PostMapping("/set_aes_key")
    public void setAesKey(UserData userData, @Valid @RequestBody SetAesKeyDTO dto) {
        byte[] secretKey = userService.setEncryptionKey(userData, dto.getPasswd(), dto.aes_key);
        userEncryptionKeyService.setEncryptionKey(secretKey);
    }

    @PostMapping("/enable_2fa")
    public void enable2FA(UserData userData, @Valid @RequestBody VerifyTotpTokenDTO dto) {
        userService.enable2FA(userData, dto.totpToken);
    }

    @PostMapping("/disable_2fa")
    public void disable2fa(UserData userData) {
        userService.disable2FA(userData);
    }

    @PostMapping("/update_user")
    public UserDTO updateUser(UserData userData, @Valid @RequestBody UpdateUserDTO dto) {
        userService.updateUser(userData, dto);
        return userService.getUserDTO(userData, userEncryptionKeyService.getEncryptionKey());
    }

    @PostMapping("/change_passwd")
    public void changeLoginPassword(UserData userData, @Valid @RequestBody ChangePasswordDTO dto) {
        userService.changeLoginPassword(userData,
                                        userEncryptionKeyService.getEncryptionKey(),
                                        dto.getPasswd(),
                                        dto.passwd_new);
    }

    @PostMapping("/request_for_storage")
    public void requestForStorage(UserData userData, @Valid @RequestBody RequestForStorageDTO dto) {
        userService.requestForStorage(userData, dto.number_of_mb);
    }

    @PostMapping("/delete_user")
    public void deleteUser(UserData userData,
                           @Valid @RequestBody PasswordDTO dto,
                           HttpServletRequest request,
                           HttpServletResponse response) {

        userService.deleteUser(userData, dto.getPasswd());
        userEncryptionKeyService.setEncryptionKey(null);
        new SecurityContextLogoutHandler().logout(request, response, null);
    }

    @Setter
    @Getter
    public static class InvokeTriggerStageDTO {

        @NotNull
        protected Boolean sendEmailOnlyToUser;

    }

    @Setter
    @Getter
    public static class SetAesKeyDTO extends PasswordDTO {

        @Size(max = 80)
        protected String aes_key;

    }

    @Setter
    @Getter
    public static class VerifyTotpTokenDTO {

        @NotEmpty
        @Size(min = 6, max = 6)
        protected String totpToken;

    }

    @Setter
    @Getter
    public static class UpdateUserDTO {

        @Size(max = 30)
        protected String screenName;

        protected Boolean tosAccepted;

        protected Boolean allowPasswordReset;

        protected Boolean verifyUnknownBrowsers;

        protected Lang lang;

        @Email
        @Size(max = 30)
        protected String totpRecoveryEmail;

        protected Trigger trigger;

        protected TriggerInternal triggerInternal;

    }

    @Setter
    @Getter
    public static class ChangePasswordDTO extends PasswordDTO {

        @NotEmpty
        @Size(min = 3, max = 20)
        protected String passwd_new;
    }

    @Setter
    @Getter
    public static class RequestForStorageDTO {

        @NotNull
        @Max(500)
        @Min(1)
        protected Integer number_of_mb;

    }

}
