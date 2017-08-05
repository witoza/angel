package co.postscriptum.controller;

import co.postscriptum.controller.dto.UuidDTO;
import co.postscriptum.model.bo.File;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.dto.FileDTO;
import co.postscriptum.security.UserEncryptionKeyService;
import co.postscriptum.service.FileService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/file")
@Slf4j
@AllArgsConstructor
public class FileController {

    private final FileService fileService;

    private final UserEncryptionKeyService userEncryptionKeyService;

    @GetMapping("/open")
    public ResponseEntity<InputStreamResource> open(UserData userData, @RequestParam(value = "file_uuid") String fileUuid)
            throws IOException {
        return fileService.openFile(userData,
                                    userEncryptionKeyService.requireEncryptionKey(),
                                    FilenameUtils.removeExtension(fileUuid));
    }

    @PostMapping("/upload")
    public FileDTO upload(UserData userData,
                          @RequestParam("file") MultipartFile multipartFile,
                          @RequestParam(name = "file_info[title]", required = false) String title) throws IOException {

        log.info("uploading file, originalFilename={}, contentType={}, size={}, title={}",
                 multipartFile.getOriginalFilename(),
                 multipartFile.getContentType(),
                 multipartFile.getSize(),
                 title);

        File file = fileService.upload(userData,
                                       userEncryptionKeyService.requireEncryptionKey(),
                                       multipartFile, title);

        return fileService.convertToDto(userData, file);
    }

    @PostMapping("/get_files")
    public List<FileDTO> getFiles(UserData userData) {
        return userData.getFiles()
                       .stream()
                       .map(file -> fileService.convertToDto(userData, file))
                       .collect(Collectors.toList());
    }

    @PostMapping("/delete_file")
    public void deleteFile(UserData userData, @Valid @RequestBody UuidDTO dto) throws IOException {
        fileService.deleteFile(userData, dto.uuid);
    }

    @PostMapping("/decrypt")
    public void decrypt(UserData userData, @Valid @RequestBody DeEncryptDTO dto) throws IOException {
        fileService.decrypt(userData,
                            userEncryptionKeyService.requireEncryptionKey(),
                            dto.msg_uuid,
                            dto.file_uuid,
                            dto.encryption_passwd);
    }

    @PostMapping("/encrypt")
    public void encrypt(UserData userData, @Valid @RequestBody DeEncryptDTO dto) throws IOException {
        fileService.encrypt(userData, dto.msg_uuid, dto.file_uuid, dto.encryption_passwd);
    }

    @Getter
    @Setter
    public static class DeEncryptDTO {

        @NotEmpty
        @Size(min = 34, max = 34)
        String msg_uuid;

        @NotEmpty
        @Size(min = 34, max = 35)
        String file_uuid;

        @NotEmpty
        @Size(max = 30)
        String encryption_passwd;

    }

}
