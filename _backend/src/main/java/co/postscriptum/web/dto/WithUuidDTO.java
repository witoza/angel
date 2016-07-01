package co.postscriptum.web.dto;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Size;

@Getter
@Setter
public class WithUuidDTO {

    @NotEmpty
    @Size(min = 30, max = 50)
    public String uuid;

}