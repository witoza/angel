package co.postscriptum.service;

import co.postscriptum.exceptions.ExceptionBuilder;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.File;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.Notification;
import co.postscriptum.model.bo.ReleaseItem;
import co.postscriptum.model.bo.UserData;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
public class UserDataHelper {

    private final UserData userData;

    public ReleaseItem requireReleaseItem(Message message, String releaseKey) {
        return message
                .getRelease()
                .getItems()
                .stream()
                .filter(ri -> ri.getKey().equals(releaseKey))
                .findFirst()
                .orElseThrow(ExceptionBuilder.missingClass(ReleaseItem.class, "releaseKey=" + releaseKey));
    }

    public Message requireMessageByUuid(String uuid) {
        return userData.getMessages()
                       .stream()
                       .filter(message -> message.getUuid().equals(uuid))
                       .findFirst()
                       .orElseThrow(ExceptionBuilder.missingClass(Message.class, "uuid=" + uuid));
    }

    public Optional<File> getFileByUuid(String uuid) {
        return userData.getFiles()
                       .stream()
                       .filter(file -> file.getUuid().equals(uuid))
                       .findFirst();
    }

    public File requireFileByUuid(String uuid) {
        return getFileByUuid(uuid)
                .orElseThrow(ExceptionBuilder.missingClass(File.class, "uuid=" + uuid));
    }


    public void addNotification(String message) {

        List<Notification> notifications = userData.getNotifications();

        notifications.add(Notification.builder()
                                      .createdTime(System.currentTimeMillis())
                                      .uuid(Utils.randKey("NOTIF"))
                                      .msg(message)
                                      .build());

        Utils.limit(notifications, 30);
    }
}
