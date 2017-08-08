package co.postscriptum.model.dto;

import co.postscriptum.model.bo.Lang;
import co.postscriptum.model.bo.Message.Type;
import co.postscriptum.model.bo.PasswordEncryption;
import co.postscriptum.model.bo.ReleaseItem;
import lombok.Data;

import java.util.List;

@Data
public class MessageDTO {

    private String uuid;

    private long creationTime;

    private long updateTime;

    private Type type;

    private Lang lang;

    private String title;

    private List<String> recipients;

    private List<String> attachments;

    private List<FileDTO> files;

    private String content;

    private ReleaseItem releaseItem;

    private PasswordEncryption passwordEncryption;

}
