package co.postscriptum.exception;

public class InternalException extends RuntimeException {

    public InternalException(String msg) {
        super(msg);
    }

    public InternalException(String msg, Exception e) {
        super(msg, e);
    }

}