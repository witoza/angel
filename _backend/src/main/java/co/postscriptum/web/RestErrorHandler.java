package co.postscriptum.web;

import co.postscriptum.exceptions.BadRequestException;
import co.postscriptum.exceptions.ForbiddenException;
import co.postscriptum.exceptions.InternalException;
import co.postscriptum.internal.Utils;
import co.postscriptum.metrics.RestMetrics;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@ControllerAdvice
@Slf4j
public class RestErrorHandler {

    @Value("${spring.http.multipart.max-file-size}")
    private String uploadMaxFileSize;

    @Autowired
    private RestMetrics restMetrics;

    @ExceptionHandler(Exception.class)
    public void handleConflict(HttpServletRequest request, HttpServletResponse response, Exception thrown)
            throws Exception {

        // If the exception is annotated with @ResponseStatus rethrow it and let
        // the framework handle it
        if (AnnotationUtils.findAnnotation(thrown.getClass(), ResponseStatus.class) != null) {
            log.info("rethrow it as annotated ResponseStatus");
            throw thrown;
        }

        log.warn("handling exception: {}", Utils.exceptionInfo(thrown));

        if (thrown instanceof MultipartException) {
            thrown = new BadRequestException("Can't upload file bigger than " + uploadMaxFileSize, thrown);
        }

        if (thrown instanceof MethodArgumentNotValidException) {
            thrown = new BadRequestException("Invalid input data", thrown);
        }

        if (thrown instanceof IOException) {
            thrown = new InternalException("Some internal problem occurred, we have been notified about the issue", thrown);
        }

        ErrorResponse jr = new ErrorResponse();
        if (thrown instanceof BadRequestException) {
            jr.setCode(400);
        } else if (thrown instanceof ForbiddenException) {
            jr.setCode(403);
        } else if (thrown instanceof InternalException) {
            jr.setCode(500);
            log.error("internal error occurred", thrown);
        } else {
            jr.setCode(400);
            log.warn("unknown error occurred", thrown);
        }
        jr.setMessage(thrown.getMessage());

        restMetrics.reportException(request, thrown);

        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().println(Utils.toJson(jr));
        response.setStatus(jr.getCode());
    }

    @Data
    private static class ErrorResponse {
        int code;
        String message;
    }
}