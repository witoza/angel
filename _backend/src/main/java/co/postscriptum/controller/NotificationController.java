package co.postscriptum.controller;

import co.postscriptum.controller.dto.UuidDTO;
import co.postscriptum.model.bo.Notification;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.service.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/notif")
@AllArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/all")
    public List<Notification> all(UserData userData) {
        return userData.getNotifications();
    }

    @PostMapping("/mark_as_read")
    public void markAsRead(UserData userData, @Valid @RequestBody UuidDTO dto) {
        notificationService.markAsRead(userData, dto.uuid);
    }

}
