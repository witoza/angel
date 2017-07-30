package co.postscriptum.controller.dto;

import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Size;

@Data
public class PasswordDTO {

    @NotEmpty
    @Size(min = 3, max = 20)
    public String passwd;

}
