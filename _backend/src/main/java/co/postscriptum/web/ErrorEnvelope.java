package co.postscriptum.web;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class ErrorEnvelope {

    private int code;

    private String message;

}
