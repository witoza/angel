package co.postscriptum.model;

import co.postscriptum.model.bo.File;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.PasswordEncryption;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.dto.FileDTO;
import co.postscriptum.model.dto.MessageDTO;
import co.postscriptum.service.UserDataHelper;

import java.util.ArrayList;

public class BO2DTOConverter {

    public static MessageDTO toMessageDTO(Message msg) {

        MessageDTO mdto = new MessageDTO();

        mdto.setUuid(msg.getUuid());
        mdto.setCreationTime(msg.getCreationTime());
        mdto.setUpdateTime(msg.getUpdateTime());
        mdto.setType(msg.getType());
        mdto.setLang(msg.getLang());
        mdto.setTitle(msg.getTitle());
        mdto.setRecipients(msg.getRecipients());
        mdto.setAttachments(msg.getAttachments());
        mdto.setFiles(new ArrayList<>());

        if (msg.getEncryption() != null) {
            mdto.setPasswordEncryption(PasswordEncryption.builder()
                                                         .hint(msg.getEncryption().getHint())
                                                         .build());
        }

        return mdto;
    }

    public static FileDTO toFileDTO(UserData userData, File file) {

        FileDTO fdto = new FileDTO();

        if (file.getBelongsTo() != null) {
            fdto.setBelongsTo(toMessageDTO(new UserDataHelper(userData).requireMessageByUuid(file.getBelongsTo())));
        }

        fdto.setUuid(file.getUuid());
        fdto.setName(file.getName());
        fdto.setExt(file.getExt());
        fdto.setMime(file.getMime());
        fdto.setUploadTime(file.getUploadTime());
        fdto.setSize(file.getSize());
        fdto.setSha1(file.getSha1());
        fdto.setMessages(new ArrayList<>());
        fdto.setPasswordEncrypted(file.getEncryption() != null);

        return fdto;
    }
}
