package co.postscriptum.model.bo;

import lombok.Data;

import java.util.List;

@Data
public class UserData {

    User user;
    UserInternal internal;

    List<Message> messages;
    List<File> files;
    List<Notification> notifications;
    List<RequiredAction> requiredActions;

}
