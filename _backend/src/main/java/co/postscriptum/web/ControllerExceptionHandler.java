package co.postscriptum.web;

import co.postscriptum.exception.ForbiddenException;
import co.postscriptum.exception.InternalException;
import co.postscriptum.internal.Utils;
import co.postscriptum.metrics.RestMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MultipartException;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@ControllerAdvice
@Slf4j
public class ControllerExceptionHandler {

    @Value("${spring.http.multipart.max-file-size}")
    private String uploadMaxFileSize;

    @Autowired
    private RestMetrics restMetrics;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleException(HttpServletRequest request, Exception thrown) {

        log.warn("handling exception: {}", Utils.exceptionInfo(thrown));

        restMetrics.reportException(request, thrown);

        ErrorEnvelope errorEnvelope = handleException(thrown);

        log.info("errorEnvelope={}", errorEnvelope);

        return new ResponseEntity<>(errorEnvelope, HttpStatus.valueOf(errorEnvelope.getCode()));
    }

    private ErrorEnvelope handleException(Exception thrown) {
        if (thrown instanceof MultipartException) {
            return new ErrorEnvelope(HttpStatus.BAD_REQUEST.value(), "Can't upload file bigger than " + uploadMaxFileSize);
        }
        if (thrown instanceof ForbiddenException) {
            return new ErrorEnvelope(HttpStatus.FORBIDDEN.value(), thrown.getMessage());
        }
        if (thrown instanceof MethodArgumentNotValidException) {
            return new ErrorEnvelope(HttpStatus.BAD_REQUEST.value(), "Invalid input data");
        }
        if (thrown instanceof IOException ||
                thrown instanceof InternalException) {

            log.info("full stack trace", thrown);

            return new ErrorEnvelope(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal problem occurred, we have been notified about the issue");
        }
        return new ErrorEnvelope(HttpStatus.BAD_REQUEST.value(), "Bad request");
    }

}
