package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
public class File {

    private String uuid;

    private String originalFileUuid;

    private String belongsTo;

    private String name;

    private String ext;

    private String mime;

    private long uploadTime;

    private long size;

    private String sha1;

    private byte[] iv;

    private PasswordEncryption encryption;

    public String getFilename() {
        if (StringUtils.isEmpty(ext)) {
            return name;
        }
        if (name.endsWith("." + ext)) {
            return name;
        }
        return name + "." + ext;
    }

}
