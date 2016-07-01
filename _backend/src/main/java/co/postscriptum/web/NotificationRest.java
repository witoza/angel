package co.postscriptum.web;

import co.postscriptum.model.bo.Notification;
import co.postscriptum.service.NotificationService;
import co.postscriptum.web.dto.WithUuidDTO;
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
public class NotificationRest {

    private final NotificationService notificationService;

    @GetMapping("/all")
    public List<Notification> all() {
        return notificationService.getNotifications();
    }

    @PostMapping("/mark_as_read")
    public void markAsRead(@Valid @RequestBody WithUuidDTO dto) {

        notificationService.markAsRead(dto.uuid);

    }

}
