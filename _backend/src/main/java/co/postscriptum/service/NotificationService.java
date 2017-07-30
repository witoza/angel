package co.postscriptum.service;

import co.postscriptum.exception.ExceptionBuilder;
import co.postscriptum.model.bo.Notification;
import co.postscriptum.model.bo.UserData;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class NotificationService {

    public void markAsRead(UserData userData, String uuid) {
        userData.getNotifications()
                .stream()
                .filter(n -> n.getUuid().equals(uuid))
                .findFirst()
                .orElseThrow(ExceptionBuilder.missingClass(Notification.class, "uuid=" + uuid))
                .setRead(true);
    }

}
