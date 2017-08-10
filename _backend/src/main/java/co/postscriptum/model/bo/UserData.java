package co.postscriptum.model.bo;

import co.postscriptum.exception.ExceptionBuilder;
import co.postscriptum.exception.ForbiddenException;
import co.postscriptum.internal.Utils;
import co.postscriptum.security.PasswordUtils;
import co.postscriptum.security.RequestMetadata;
import lombok.Data;

import java.util.List;
import java.util.Optional;

@Data
public class UserData {

    private User user;

    private UserInternal internal;

    private List<Message> messages;

    private List<File> files;

    private List<Notification> notifications;

    private List<RequiredAction> requiredActions;

    public void addNotification(String notificationMessage) {
        notifications.add(Notification.builder()
                                      .createdTime(System.currentTimeMillis())
                                      .uuid(Utils.randKey("N"))
                                      .msg(notificationMessage)
                                      .build());
        Utils.limit(notifications, 30);
    }

    public RequiredAction requireRequiredActionByUuid(String actionUuid) {
        return requiredActions.stream()
                              .filter(ra -> ra.getUuid().equals(actionUuid))
                              .findFirst()
                              .orElseThrow(ExceptionBuilder.missingObject(RequiredAction.class, "uuid=" + actionUuid));
    }

    public Message requireMessageByUuid(String uuid) {
        return messages.stream()
                       .filter(message -> message.getUuid().equals(uuid))
                       .findFirst()
                       .orElseThrow(ExceptionBuilder.missingObject(Message.class, "uuid=" + uuid));
    }

    public Optional<File> getFileByUuid(String uuid) {
        return files.stream()
                    .filter(file -> file.getUuid().equals(uuid))
                    .findFirst();
    }

    public File requireFileByUuid(String uuid) {
        return getFileByUuid(uuid)
                .orElseThrow(ExceptionBuilder.missingObject(File.class, "uuid=" + uuid));
    }

    public long getUserUsedSpaceBytes() {
        long total = 0;
        for (File file : files) {
            total += file.getSize();
            total += file.getName().length();
            if (file.getExt() != null) {
                total += file.getExt().length();
            }
        }
        for (Message message : messages) {
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
        long quotaBytes = internal.getQuotaBytes();

        if (usedBytes + addBytes > quotaBytes) {
            throw new ForbiddenException(
                    "You don't have enough storage space to do that. Increase your quota and try again. Currently used: "
                            + kb(usedBytes) + "kb, would be: " + kb(usedBytes + addBytes) + "kb, quota: " + kb(quotaBytes) + "kb.");

        }
    }

    public boolean isUserAdmin() {
        return user.getRole() == User.Role.admin;
    }

    public void verifyLoginPasswordIsCorrect(String loginPassword) {
        if (!PasswordUtils.checkPasswordHash(loginPassword, internal.getPasswordHash())) {
            throw new ForbiddenException("Invalid login password");
        }
    }

    public void addLoginAttempt(RequestMetadata metadata, String type) {
        List<LoginAttempt> loginHistory = internal.getLoginHistory();
        loginHistory.add(LoginAttempt.builder()
                                     .time(System.currentTimeMillis())
                                     .ip(metadata.getRemoteIp())
                                     .type(type)
                                     .build());
        Utils.limit(loginHistory, 20);
    }

}
