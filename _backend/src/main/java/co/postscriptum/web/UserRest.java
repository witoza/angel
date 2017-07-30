package co.postscriptum.web;

import co.postscriptum.exception.BadRequestException;
import co.postscriptum.internal.QRGenerator;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.Lang;
import co.postscriptum.model.bo.LoginAttempt;
import co.postscriptum.model.bo.Trigger;
import co.postscriptum.model.bo.TriggerInternal;
import co.postscriptum.model.dto.UserDTO;
import co.postscriptum.service.UserService;
import co.postscriptum.web.dto.WithPasswordDTO;
import com.google.zxing.WriterException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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
public class UserRest {

    private final UserService userService;

    @GetMapping("/current")
    public UserDTO current() {
        return userService.getUserDTO();
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            throw new BadRequestException("user already logged out");
        }

        userService.unloadUser();

        new SecurityContextLogoutHandler().logout(request, null, null);
    }

    @PostMapping("/send_x_notification")
    public List<String> sendTriggerAfterX(@Valid @RequestBody InvokeTriggerStageDTO dto) {
        return userService.sendTriggerAfterX(dto.getSendEmailOnlyToUser());
    }

    @PostMapping("/send_y_notification")
    public List<String> sendTriggerAfterY(@Valid @RequestBody InvokeTriggerStageDTO dto) {
        return userService.sendTriggerAfterY(dto.getSendEmailOnlyToUser());
    }

    @PostMapping("/send_z_notification")
    public List<String> sendTriggerAfterZ(@Valid @RequestBody InvokeTriggerStageDTO dto) {
        return userService.sendTriggerAfterZ(dto.getSendEmailOnlyToUser());
    }

    @PostMapping("/generate_totp_secret")
    public void generateTotpSecret() {
        userService.generateTotpSecret();
    }

    @GetMapping("/totpQR")
    public ResponseEntity<InputStreamResource> totpQr() {
        return userService.getTotpUriQr();
    }

    @PostMapping("/get_login_history")
    public List<LoginAttempt> getLoginHistory() {
        return userService.getLoginHistory();
    }

    @GetMapping("/get_qr")
    public ResponseEntity<InputStreamResource> getQr(@RequestParam("data") String data)
            throws WriterException, IOException {
        return QRGenerator.getQR(data);
    }

    @PostMapping("/get_bitcoin_address")
    public Map<String, String> getBitcoinAddress() {
        return Collections.singletonMap("address", userService.getPaymentBitcoinAddress());
    }

    @PostMapping("/get_aes_key")
    public Map<String, String> getAesKey() {

        String aesKey = userService.getUserEncryptionKey()
                                   .map(key -> Utils.base32encode(key.getEncoded()))
                                   .orElse(null);

        return Collections.singletonMap("aes_key", aesKey);
    }

    @PostMapping("/set_aes_key")
    public void setAesKey(@Valid @RequestBody SetAesKeyDTO dto) {
        userService.setEncryptionKey(dto.passwd, dto.aes_key);
    }

    @PostMapping("/enable_2fa")
    public void enable2FA(@Valid @RequestBody VerifyTotpTokenDTO dto) {
        userService.enable2FA(dto.totpToken);
    }

    @PostMapping("/disable_2fa")
    public void disable2fa() {
        userService.disable2FA();
    }

    @PostMapping("/update_user")
    public UserDTO updateUser(@Valid @RequestBody UpdateUserDTO dto) {

        userService.updateUser(dto);

        return userService.getUserDTO();
    }

    @PostMapping("/change_passwd")
    public void changeLoginPassword(@Valid @RequestBody ChangePasswordDTO dto) {

        userService.changeLoginPassword(dto.passwd, dto.passwd_new);

    }

    @PostMapping("/request_for_storage")
    public void requestForStorage(@Valid @RequestBody RequestForStorageDTO dto) {

        userService.requestForStorage(dto.number_of_mb);

    }

    @PostMapping("/delete_user")
    public void deleteUser(@Valid @RequestBody WithPasswordDTO dto, HttpServletRequest request) {

        userService.deleteUser(dto.passwd);

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
    public static class SetAesKeyDTO extends WithPasswordDTO {

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
    public static class ChangePasswordDTO extends WithPasswordDTO {

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
