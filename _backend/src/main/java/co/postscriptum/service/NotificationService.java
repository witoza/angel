package co.postscriptum.service;

import co.postscriptum.exceptions.ExceptionBuilder;
import co.postscriptum.model.bo.Notification;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class NotificationService {

    private final LoggedUserService loggedUserDS;

    public void markAsRead(String uuid) {
        getNotifications()
                .stream()
                .filter(n -> n.getUuid().equals(uuid))
                .findFirst()
                .orElseThrow(ExceptionBuilder.missingClass(Notification.class, "uuid=" + uuid))
                .setRead(true);
    }

    public List<Notification> getNotifications() {
        return loggedUserDS.requireUserData().getNotifications();
    }

}
