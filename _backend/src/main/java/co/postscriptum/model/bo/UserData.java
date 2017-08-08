package co.postscriptum.model.bo;

import lombok.Data;

import java.util.List;

@Data
public class UserData {

    private User user;

    private UserInternal internal;

    private List<Message> messages;

    private List<File> files;

    private List<Notification> notifications;

    private List<RequiredAction> requiredActions;

}
