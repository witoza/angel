package co.postscriptum.model.dto;

import co.postscriptum.model.bo.Lang;
import co.postscriptum.model.bo.Message.Type;
import co.postscriptum.model.bo.PasswordEncryption;
import co.postscriptum.model.bo.ReleaseItem;
import lombok.Data;

import java.util.List;

@Data
public class MessageDTO {

    String uuid;

    long creationTime;
    long updateTime;

    Type type;
    Lang lang;

    String title;
    List<String> recipients;

    List<String> attachments;
    List<FileDTO> files;

    String content;
    ReleaseItem releaseItem;
    PasswordEncryption passwordEncryption;

}
