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
import co.postscriptum.service.UserService;
import co.postscriptum.web.AuthenticationHelper;
import com.google.zxing.WriterException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
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
public class UserController {

    private final UserService userService;

    @GetMapping("/current")
    public UserDTO current(UserData userData) {
        return userService.getUserDTO(userData, AuthenticationHelper.getUserEncryptionKey());
    }

    @PostMapping("/logout")
    public void logout(UserData userData, HttpServletRequest request) {

        if (AuthenticationHelper.getAuthentication() == null) {
            throw new BadRequestException("user already logged out");
        }

        userService.unloadUser(userData);

        new SecurityContextLogoutHandler().logout(request, null, null);
    }

    @PostMapping("/send_x_notification")
    public List<String> sendTriggerAfterX(UserData userData, @Valid @RequestBody InvokeTriggerStageDTO dto) {
        return userService.sendTriggerAfterX(userData, dto.getSendEmailOnlyToUser());
    }

    @PostMapping("/send_y_notification")
    public List<String> sendTriggerAfterY(UserData userData, @Valid @RequestBody InvokeTriggerStageDTO dto) {
        return userService.sendTriggerAfterY(userData, dto.getSendEmailOnlyToUser());
    }

    @PostMapping("/send_z_notification")
    public List<String> sendTriggerAfterZ(UserData userData, @Valid @RequestBody InvokeTriggerStageDTO dto) {
        return userService.sendTriggerAfterZ(userData, dto.getSendEmailOnlyToUser());
    }

    @PostMapping("/generate_totp_secret")
    public void generateTotpSecret(UserData userData) {
        userService.generateTotpSecret(userData);
    }

    @GetMapping("/totpQR")
    public ResponseEntity<InputStreamResource> totpQr(UserData userData) {
        return userService.getTotpUriQr(userData);
    }

    @PostMapping("/get_login_history")
    public List<LoginAttempt> getLoginHistory(UserData userData) {
        return userData.getInternal().getLoginHistory();
    }

    @GetMapping("/get_qr")
    public ResponseEntity<InputStreamResource> getQr(@RequestParam("data") String data)
            throws WriterException, IOException {
        return QRGenerator.getQR(data);
    }

    @PostMapping("/get_bitcoin_address")
    public Map<String, String> getBitcoinAddress(UserData userData) {
        return Collections.singletonMap("address", userService.getPaymentBitcoinAddress(userData));
    }

    @PostMapping("/get_aes_key")
    public Map<String, String> getAesKey() {

        String aesKey = AuthenticationHelper.getUserEncryptionKey()
                                            .map(key -> Utils.base32encode(key.getEncoded()))
                                            .orElse(null);

        return Collections.singletonMap("aes_key", aesKey);
    }

    @PostMapping("/set_aes_key")
    public void setAesKey(UserData userData, @Valid @RequestBody SetAesKeyDTO dto) {

        byte[] secretKey = userService.setEncryptionKey(userData,
                                                        AuthenticationHelper.getUserEncryptionKey(),
                                                        dto.passwd,
                                                        dto.aes_key);

        AuthenticationHelper.setUserEncryptionKey(secretKey);

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

        return userService.getUserDTO(userData, AuthenticationHelper.getUserEncryptionKey());
    }

    @PostMapping("/change_passwd")
    public void changeLoginPassword(UserData userData, @Valid @RequestBody ChangePasswordDTO dto) {

        userService.changeLoginPassword(userData,
                                        AuthenticationHelper.getUserEncryptionKey(),
                                        dto.passwd,
                                        dto.passwd_new);

    }

    @PostMapping("/request_for_storage")
    public void requestForStorage(UserData userData, @Valid @RequestBody RequestForStorageDTO dto) {

        userService.requestForStorage(userData, dto.number_of_mb);

    }

    @PostMapping("/delete_user")
    public void deleteUser(UserData userData, @Valid @RequestBody PasswordDTO dto, HttpServletRequest request) {

        userService.deleteUser(userData, dto.passwd);

        new SecurityContextLogoutHandler().logout(request, null, null);

    }

    @Setter
    @Getter
    public static class InvokeTriggerStageDTO {

        @NotNull
        Boolean sendEmailOnlyToUser;

    }

    @Setter
    @Getter
    public static class SetAesKeyDTO extends PasswordDTO {

        @Size(max = 80)
        String aes_key;

    }

    @Setter
    @Getter
    public static class VerifyTotpTokenDTO {

        @NotEmpty
        @Size(min = 6, max = 6)
        String totpToken;

    }

    @Setter
    @Getter
    public static class UpdateUserDTO {

        @Size(max = 30)
        String screenName;

        Boolean tosAccepted;
        Boolean allowPasswordReset;
        Boolean verifyUnknownBrowsers;
        Lang lang;

        @Email
        @Size(max = 30)
        String totpRecoveryEmail;

        Trigger trigger;
        TriggerInternal triggerInternal;

    }

    @Setter
    @Getter
    public static class ChangePasswordDTO extends PasswordDTO {

        @NotEmpty
        @Size(min = 3, max = 20)
        String passwd_new;
    }

    @Setter
    @Getter
    public static class RequestForStorageDTO {

        @NotNull
        @Max(500)
        @Min(1)
        Integer number_of_mb;
    }

}
