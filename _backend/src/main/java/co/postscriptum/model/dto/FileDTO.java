package co.postscriptum.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class FileDTO {

    private String uuid;

    private String name;

    private String ext;

    private MessageDTO belongsTo;

    private String mime;

    private long uploadTime;

    private long size;

    private String sha1;

    private List<MessageDTO> messages;

    private boolean passwordEncrypted;

}
