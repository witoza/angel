package co.postscriptum.exception;

public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String msg) {
        super(msg);
    }

    public ForbiddenException(String msg, Exception e) {
        super(msg, e);
    }

}