package co.postscriptum.controller;

import co.postscriptum.model.bo.File;
import co.postscriptum.model.dto.MessageDTO;
import co.postscriptum.service.PreviewService;
import co.postscriptum.web.ResponseEntityUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@RestController
@RequestMapping("/preview")
@AllArgsConstructor
public class PreviewController {

    private final PreviewService previewService;

    @PostMapping("/download")
    public ResponseEntity<InputStreamResource> download(@Valid @ModelAttribute DownloadFileDTO dto) throws IOException {

        Pair<File, InputStream> fileContent = previewService.download(dto);

        return ResponseEntityUtils.asAttachment(fileContent.getRight(),
                                                fileContent.getLeft().getMime(),
                                                fileContent.getKey().getFilename());
    }

    @PostMapping("/decrypt")
    public MessageDTO decrypt(@Valid @RequestBody PreviewBaseDTO dto) {
        return previewService.decrypt(dto);
    }

    @PostMapping("/get_by_uuid")
    public Map<String, Object> getByUuid(@Valid @RequestBody PreviewBaseDTO dto) {
        return previewService.requireMessage(dto);
    }

    @Setter
    @Getter
    public static class PreviewBaseDTO {

        @Size(max = 80)
        protected String encryptionKey;

        @Size(max = 60)
        protected String recipientKey;

        @Size(max = 60)
        protected String releaseKey;

        @Size(max = 20)
        protected String encryptionPassword;

        @NotEmpty
        @Size(min = 30, max = 50)
        protected String user_uuid;

        @Size(max = 34)
        protected String msg_uuid;

    }

    @Setter
    @Getter
    public static class DownloadFileDTO extends PreviewBaseDTO {

        @NotEmpty
        @Size(min = 34, max = 35)
        protected String file_uuid;

    }

}
