package co.postscriptum.web.dto;

import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Size;

@Data
public class WithPasswordDTO {

    @NotEmpty
    @Size(min = 3, max = 20)
    public String passwd;

}
