package co.postscriptum.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class FileDTO {

    String uuid;
    String name;
    String ext;
    MessageDTO belongsTo;
    String mime;
    long uploadTime;
    long size;
    String sha1;

    List<MessageDTO> messages;

    boolean passwordEncrypted;

}
