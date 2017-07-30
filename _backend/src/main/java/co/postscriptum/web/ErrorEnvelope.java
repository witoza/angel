package co.postscriptum.web;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorEnvelope {

    private int code;

    private String message;

}
