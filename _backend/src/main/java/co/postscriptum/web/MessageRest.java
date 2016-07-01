package co.postscriptum.web;

import co.postscriptum.exceptions.BadRequestException;
import co.postscriptum.model.bo.Lang;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.Message.Type;
import co.postscriptum.model.dto.MessageDTO;
import co.postscriptum.service.MessageService;
import co.postscriptum.web.dto.WithUuidDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.SafeHtml;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/msg")
@AllArgsConstructor
public class MessageRest {

    private final MessageService messageService;

    @PostMapping(value = "/remove_password")
    public MessageDTO removePassword(@Valid @RequestBody EncryptedMsgDTO dto) {
        return messageService.convertToDto(messageService.removePassword(dto.uuid, dto.encryption_passwd));
    }

    @PostMapping(value = "/set_password")
    public MessageDTO setPassword(@Valid @RequestBody SetMsgPasswordDTO dto) {
        return messageService.convertToDto(messageService.setPassword(dto.uuid,
                                                                      dto.encryption_passwd,
                                                                      dto.encryption_passwd_new,
                                                                      dto.hint));
    }

    @PostMapping(value = "/decrypt")
    public MessageDTO decrypt(@Valid @RequestBody EncryptedMsgDTO dto) {

        Message message = messageService.requireMessageByUuid(dto.uuid);

        if (message.getEncryption() == null) {
            throw new BadRequestException("expected encrypted message");
        }

        MessageDTO mdto = messageService.convertToDto(message);

        mdto.setContent(messageService.getMessageContent(message, dto.encryption_passwd));

        return mdto;
    }

    @GetMapping(value = "/get_abstract")
    public List<MessageDTO> getAbstract() {

        return messageService.getMessages()
                             .stream()
                             .map(messageService::convertToDtoSimplified)
                             .collect(Collectors.toList());
    }

    @PostMapping(value = "/load_msg")
    public MessageDTO loadMsg(@Valid @RequestBody WithUuidDTO dto) {

        Message message = messageService.requireMessageByUuid(dto.uuid);

        MessageDTO mdto = messageService.convertToDto(message);

        if (message.getEncryption() == null) {
            mdto.setContent(messageService.getMessageContent(message, null));
        }

        return mdto;
    }

    @PostMapping(value = "/delete_msg")
    public void deleteMsg(@Valid @RequestBody WithUuidDTO dto) {

        messageService.deleteMessage(dto.uuid);

    }

    @PostMapping(value = "/add_msg")
    public MessageDTO addMsg(@Valid @RequestBody AddMsgDTO dto) {

        Message message = messageService.addMessage(dto);

        MessageDTO mdto = messageService.convertToDto(message);

        if (message.getEncryption() == null) {
            mdto.setContent(messageService.getMessageContent(message, null));
        }

        return mdto;
    }

    @PostMapping(value = "/update_msg")
    public MessageDTO updateMsg(@Valid @RequestBody UpdateMsgDTO dto) {

        Message message = messageService.updateMessage(dto);

        MessageDTO mdto = messageService.convertToDto(message);

        mdto.setContent(messageService.getMessageContent(message, dto.encryption_passwd));

        return mdto;
    }

    @Getter
    @Setter
    private static class EncryptedMsgDTO extends WithUuidDTO {

        @NotEmpty
        @Size(max = 20)
        String encryption_passwd;
    }

    @Getter
    @Setter
    private static class SetMsgPasswordDTO extends WithUuidDTO {

        @Size(max = 20)
        String encryption_passwd;

        @NotEmpty
        @Size(max = 20)
        String encryption_passwd_new;

        @NotEmpty
        @Size(max = 100)
        String hint;
    }

    @Getter
    @Setter
    public static class AddMsgDTO {
        @NotNull
        @SafeHtml
        String content;

        @NotNull
        @Size(max = 50)
        String title;

        @NotNull
        @Size(max = 20)
        List<String> recipients;

        @NotNull
        @Size(max = 20)
        List<String> attachments;

        @NotNull
        Type type;
    }

    @Getter
    @Setter
    public static class UpdateMsgDTO extends WithUuidDTO {

        @SafeHtml
        String content;

        @Size(max = 50)
        String title;

        @Size(max = 20)
        List<String> recipients;

        @Size(max = 20)
        List<String> attachments;

        Type type;

        @Size(max = 20)
        String encryption_passwd;

        Lang lang;

    }

}
