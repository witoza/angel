package co.postscriptum.controller;

import co.postscriptum.model.dto.MessageDTO;
import co.postscriptum.service.PreviewService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
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
import java.util.Map;

@RestController
@RequestMapping("/preview")
@AllArgsConstructor
public class PreviewController {

    private final PreviewService previewService;

    @PostMapping("/download")
    public ResponseEntity<InputStreamResource> download(@Valid @ModelAttribute DownloadFileDTO dto) throws IOException {
        return previewService.download(dto);
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
        String encryptionKey;

        @Size(max = 60)
        String recipientKey;

        @Size(max = 60)
        String releaseKey;

        @Size(max = 20)
        String encryptionPassword;

        @NotEmpty
        @Size(min = 30, max = 50)
        String user_uuid;

        @Size(max = 34)
        String msg_uuid;

    }

    @Setter
    @Getter
    public static class DownloadFileDTO extends PreviewBaseDTO {

        @NotEmpty
        @Size(min = 34, max = 35)
        String file_uuid;
    }

}
