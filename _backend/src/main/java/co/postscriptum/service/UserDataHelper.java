package co.postscriptum.service;

import co.postscriptum.exception.ExceptionBuilder;
import co.postscriptum.exception.ForbiddenException;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.File;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.Notification;
import co.postscriptum.model.bo.ReleaseItem;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.security.PasswordUtils;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
public class UserDataHelper {

    private final UserData userData;

    public ReleaseItem requireReleaseItem(Message message, String releaseKey) {
        return message.getRelease()
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

    public long getUserUsedSpaceBytes() {
        long total = 0;

        for (File file : userData.getFiles()) {
            total += file.getSize();
            total += file.getName().length();
        }
        for (Message message : userData.getMessages()) {
            total += message.getContent().getCt().length;
            total += message.getTitle().length();
        }
        return total;
    }

    private long kb(long bytes) {
        return bytes / 1024;
    }

    public void verifyQuotaNotExceeded(long addBytes) {

        long usedBytes = getUserUsedSpaceBytes();
        long quotaBytes = userData.getInternal().getQuotaBytes();

        if (usedBytes + addBytes > quotaBytes) {
            throw new ForbiddenException(
                    "You don't have enough storage space to do that. Increase your quota and try again. Currently used: "
                            + kb(usedBytes) + "kb, would be: " + kb(usedBytes + addBytes) + "kb, quota: " + kb(quotaBytes) + "kb.");

        }
    }

    public boolean isUserAdmin() {
        return userData.getUser().getRole() == User.Role.admin;
    }

    public void verifyLoginPasswordIsCorrect(String loginPassword) {
        if (!PasswordUtils.checkPasswordHash(loginPassword, userData.getInternal().getPasswordHash())) {
            throw new ForbiddenException("Invalid login password");
        }
    }

}
