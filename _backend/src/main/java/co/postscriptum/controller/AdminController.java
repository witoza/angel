package co.postscriptum.controller;

import co.postscriptum.controller.dto.UuidDTO;
import co.postscriptum.db.DB;
import co.postscriptum.metrics.ComponentMetrics;
import co.postscriptum.metrics.EmailDeliveryMetrics;
import co.postscriptum.metrics.JVMMetrics;
import co.postscriptum.metrics.RestMetrics;
import co.postscriptum.model.bo.RequiredAction;
import co.postscriptum.model.bo.RequiredAction.Resolution;
import co.postscriptum.model.bo.RequiredAction.Status;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.service.AdminService;
import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@AllArgsConstructor
public class AdminController {

    private final DB db;

    private final EmailDeliveryMetrics emailDeliveryMetrics;

    private final RestMetrics restMetrics;

    private final ComponentMetrics componentMetrics;

    private final AdminService adminService;

    @PostMapping("/with_status")
    public List<RequiredAction> withStatus(UserData userData, @Valid @RequestBody StatusDTO dto) {
        return adminService.getRequiredAction(userData, dto.status);
    }

    @PostMapping("/get_user_encrypted_encryption_key")
    public Map<String, String> getUserEncryptedEncryptionKey(@Valid @RequestBody UuidDTO dto) {
        return Collections.singletonMap("data", adminService.getUserEncryptedEncryptionKey(dto.uuid));
    }

    @PostMapping("/issue_reject")
    public Resolution rejectIssue(UserData userData, @Valid @RequestBody RejectIssueDTO dto) {
        return adminService.rejectIssue(userData, dto.uuid, dto.input);
    }

    @PostMapping("/issue_remove")
    public void removeIssue(UserData userData, @Valid @RequestBody UuidDTO dto) {
        adminService.removeIssue(userData, dto.uuid);
    }

    @PostMapping("/issue_resolve")
    public Resolution resolveIssue(UserData userData, @Valid @RequestBody ResolveIssueDTO dto) {
        return adminService.resolveIssue(userData, dto.uuid, dto.input, dto.userEncryptionKeyBase64);
    }

    @GetMapping(value = "/metrics", produces = "text/plain")
    @ResponseBody
    public String metrics() {
        return String.join("\n",
                           new JVMMetrics().dump(),
                           componentMetrics.dump(),
                           restMetrics.dump());
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return ImmutableMap.of("db", db.getStats(),
                               "emailDelivery", emailDeliveryMetrics.getStats());
    }

    @Getter
    @Setter
    public static class StatusDTO {

        @NotNull
        Status status;

    }

    @Getter
    @Setter
    public static class RejectIssueDTO extends UuidDTO {

        @Size(max = 100)
        String input;

    }

    @Getter
    @Setter
    public static class ResolveIssueDTO extends RejectIssueDTO {

        @Size(max = 60)
        String userEncryptionKeyBase64;

    }

}
